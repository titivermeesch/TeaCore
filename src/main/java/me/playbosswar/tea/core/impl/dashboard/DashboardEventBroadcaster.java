package me.playbosswar.tea.core.impl.dashboard;

import static me.playbosswar.tea.core.impl.TeaCorePlugin.ERROR_TRACKER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;

/**
 * Fans distributed {@link CoordinationEvent}s out to every subscribed dashboard
 * WebSocket client.
 *
 * <p>
 * The broadcaster is only active on servers that run the dashboard
 * ({@code dashboard.enabled}). It subscribes to the coordination backend's
 * event stream (backed by Redis pub/sub in multi-server mode, direct dispatch
 * in standalone) and forwards a flattened JSON payload to each open WS
 * session. Sends are fire-and-forget; dead sockets are dropped on the next
 * broadcast.
 */
public final class DashboardEventBroadcaster {

    private final Set<WsContext> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger;

    public DashboardEventBroadcaster(CoordinationBackend backend, Logger logger) {
        this.logger = logger;
        backend.subscribeEvents(this::onEvent);
    }

    public void addSession(WsContext ctx) {
        sessions.add(ctx);
    }

    public void removeSession(WsContext ctx) {
        sessions.remove(ctx);
    }

    public int sessionCount() {
        return sessions.size();
    }

    /**
     * Called by the bootstrap once per second with the queue size per game so
     * the dashboard can render queue counts without polling.
     */
    public void broadcastQueueCounts(Map<String, Integer> counts) {
        broadcast(Map.of("type", "queue.counts", "counts", counts));
    }

    private void onEvent(CoordinationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("serverId", event.serverId());
        payload.put("arenaId", event.arenaId());
        payload.put("gameId", event.gameId());
        payload.put("playerCount", event.playerCount());
        payload.put("phaseSecondsRemaining", event.phaseSecondsRemaining());
        switch (event.type()) {
            case ARENA_STATE -> {
                payload.put("type", "arena.state");
                payload.put("from", event.fromState());
                payload.put("to", event.toState());
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
        }
        broadcast(payload);
    }

    private void broadcast(Map<String, Object> payload) {
        if (sessions.isEmpty()) return;
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to serialize dashboard event", e);
            return;
        }
        for (WsContext ctx : sessions) {
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
