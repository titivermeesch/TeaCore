package me.playbosswar.tea.core.impl.dashboard;

import static me.playbosswar.tea.core.impl.TeaCorePlugin.ERROR_TRACKER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardSessions;

/**
 * Keeps the latest kills and chat lines so a page opened mid-match can show them.
 * Chat lines only go to sessions with {@code tea.dashboard.chat}. Sends are
 * fire-and-forget; dead sockets are dropped on the next broadcast.
 */
public final class DashboardEventBroadcaster {

    private static final int ARENA_KILLS = 50;
    private static final int ARENA_CHAT = 100;
    private static final int GLOBAL_KILLS = 100;

    private final Map<WsContext, DashboardSessions.Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger;
    private final Map<String, Deque<Map<String, Object>>> killsByArena = new ConcurrentHashMap<>();
    private final Map<String, Deque<Map<String, Object>>> chatByArena = new ConcurrentHashMap<>();
    private final Deque<Map<String, Object>> globalKills = new ArrayDeque<>();

    private final java.util.function.Predicate<DashboardSessions.Session> stillValid;

    /** {@code stillValid} drops sockets whose session was signed out or expired. */
    public DashboardEventBroadcaster(CoordinationBackend backend, Logger logger,
                                     java.util.function.Predicate<DashboardSessions.Session> stillValid) {
        this.logger = logger;
        this.stillValid = stillValid;
        backend.subscribeEvents(this::onEvent);
    }

    public void addSession(WsContext ctx, DashboardSessions.Session session) {
        sessions.put(ctx, session);
    }

    public void removeSession(WsContext ctx) {
        sessions.remove(ctx);
    }

    public int sessionCount() {
        return sessions.size();
    }

    /** Every arena when {@code arenaId} is null. Oldest first. */
    public List<Map<String, Object>> recentKills(String arenaId) {
        Deque<Map<String, Object>> d = arenaId == null ? globalKills : killsByArena.get(arenaId);
        return snapshot(d);
    }

    public List<Map<String, Object>> recentChat(String arenaId) {
        return snapshot(chatByArena.get(arenaId));
    }

    private static List<Map<String, Object>> snapshot(Deque<Map<String, Object>> d) {
        if (d == null) {
            return List.of();
        }
        synchronized (d) {
            return new ArrayList<>(d);
        }
    }

    private static void push(Deque<Map<String, Object>> d, Map<String, Object> entry, int max) {
        synchronized (d) {
            d.addLast(entry);
            while (d.size() > max) {
                d.removeFirst();
            }
        }
    }

    public void broadcastQueueCounts(Map<String, Integer> counts) {
        broadcast(Map.of("type", "queue.counts", "counts", counts), null);
    }

    private void onEvent(CoordinationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("serverId", event.serverId());
        payload.put("arenaId", event.arenaId());
        payload.put("gameId", event.gameId());
        payload.put("playerCount", event.playerCount());
        payload.put("phaseSecondsRemaining", event.phaseSecondsRemaining());
        payload.put("at", Instant.now().toString());
        Map<String, String> data = event.data() == null ? Map.of() : event.data();
        String requires = null;
        switch (event.type()) {
            case ARENA_STATE -> {
                payload.put("type", "arena.state");
                payload.put("from", event.fromState());
                payload.put("to", event.toState());
                if ("RELEASED".equals(event.toState()) && event.arenaId() != null) {
                    killsByArena.remove(event.arenaId());
                    chatByArena.remove(event.arenaId());
                }
            }
            case ARENA_PLAYER_JOIN -> {
                payload.put("type", "arena.player.join");
                payload.put("playerUuid", event.playerUuid());
                payload.put("playerName", event.playerName());
                payload.put("spectator", event.spectator());
            }
            case ARENA_PLAYER_LEAVE -> {
                payload.put("type", "arena.player.leave");
                payload.put("playerUuid", event.playerUuid());
                payload.put("playerName", event.playerName());
                payload.put("reason", event.reason());
            }
            case MATCH_END -> {
                payload.put("type", "match.end");
                payload.put("winnerUuid", event.winnerUuid());
            }
            case KILL -> {
                payload.put("type", "arena.kill");
                payload.put("victimUuid", event.playerUuid());
                payload.put("victimName", event.playerName());
                payload.put("killerUuid", data.get("killer"));
                payload.put("killerName", data.get("killerName"));
                String assists = data.getOrDefault("assists", "");
                payload.put("assists", assists.isEmpty() ? List.of() : List.of(assists.split(",")));
                payload.put("cause", data.get("cause"));
                if (event.arenaId() != null) {
                    push(killsByArena.computeIfAbsent(event.arenaId(), k -> new ArrayDeque<>()), payload, ARENA_KILLS);
                }
                push(globalKills, payload, GLOBAL_KILLS);
            }
            case CHAT -> {
                payload.put("type", "arena.chat");
                payload.put("playerUuid", event.playerUuid());
                payload.put("playerName", event.playerName());
                payload.put("spectator", event.spectator());
                payload.put("message", data.getOrDefault("message", ""));
                if (event.arenaId() != null) {
                    push(chatByArena.computeIfAbsent(event.arenaId(), k -> new ArrayDeque<>()), payload, ARENA_CHAT);
                }
                requires = DashboardPermission.CHAT;
            }
            case SERVER_STATS, SETTINGS, FILES_CHANGED -> {
                return;
            }
        }
        broadcast(payload, requires);
    }

    private void broadcast(Map<String, Object> payload, String permission) {
        if (sessions.isEmpty()) return;
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to serialize dashboard event", e);
            return;
        }
        for (Map.Entry<WsContext, DashboardSessions.Session> entry : sessions.entrySet()) {
            WsContext ctx = entry.getKey();
            if (!stillValid.test(entry.getValue())) {
                sessions.remove(ctx);
                try {
                    ctx.closeSession(1008, "signed out");
                } catch (Exception ignored) {
                    // already closed
                }
                continue;
            }
            if (permission != null && !entry.getValue().can(permission)) {
                continue;
            }
            try {
                if (ctx.session.isOpen()) {
                    ctx.send(json);
                } else {
                    sessions.remove(ctx);
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Dashboard websocket send failed; dropping session", e);
                ERROR_TRACKER.trackError(e);
                sessions.remove(ctx);
            }
        }
    }
}
