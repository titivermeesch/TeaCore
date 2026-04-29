package me.playbosswar.tea.core.impl.coordination;

import static me.playbosswar.tea.core.impl.TeaCorePlugin.ERROR_TRACKER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Single-JVM coordination for {@code network.mode: standalone}. No Redis
 * needed.
 */
public final class InMemoryBackend implements CoordinationBackend {

    private final String serverId;
    private final ConcurrentHashMap<ArenaId, ArenaSnapshot> arenas = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<QueueEntry> queue = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ControlCommand>> controlHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<CoordinationEvent>> eventHandlers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, java.util.Set<GameId>> hostedGamesByServer = new ConcurrentHashMap<>();

    public InMemoryBackend(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public void publishArena(ArenaSnapshot snapshot) {
        arenas.put(snapshot.arenaId(), snapshot);
    }

    @Override
    public void removeArena(ArenaId arenaId) {
        arenas.remove(arenaId);
    }

    @Override
    public Collection<ArenaSnapshot> allArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    @Override
    public void enqueue(QueueEntry entry) {
        queue.removeIf(e -> e.playerUuid().equals(entry.playerUuid())
                && e.gameId().equals(entry.gameId()));
        queue.add(entry);
    }

    @Override
    public void dequeue(UUID playerUuid) {
        queue.removeIf(e -> e.playerUuid().equals(playerUuid));
    }

    @Override
    public Collection<QueueEntry> queueFor(GameId gameId) {
        List<QueueEntry> out = new ArrayList<>();
        for (QueueEntry e : queue) {
            if (e.gameId().equals(gameId))
                out.add(e);
        }
        out.sort(Comparator.comparing(QueueEntry::queuedAt));
        return out;
    }

    @Override
    public int queuePosition(UUID playerUuid, GameId gameId) {
        int pos = 0;
        for (QueueEntry e : queueFor(gameId)) {
            if (e.playerUuid().equals(playerUuid))
                return pos;
            pos++;
        }
        return -1;
    }

    @Override
    public java.util.Optional<QueueEntry> queueEntryOf(UUID playerUuid) {
        for (QueueEntry e : queue) {
            if (e.playerUuid().equals(playerUuid))
                return java.util.Optional.of(e);
        }
        return java.util.Optional.empty();
    }

    @Override
    public void publishHostedGames(java.util.Set<GameId> gameIds) {
        hostedGamesByServer.put(serverId, java.util.Set.copyOf(gameIds));
    }

    @Override
    public java.util.Set<String> hostsOf(GameId gameId) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (var entry : hostedGamesByServer.entrySet()) {
            if (entry.getValue().contains(gameId)) out.add(entry.getKey());
        }
        return out;
    }

    @Override
    public java.util.Set<GameId> allHostedGames() {
        java.util.Set<GameId> out = new java.util.HashSet<>();
        for (var games : hostedGamesByServer.values()) out.addAll(games);
        return out;
    }

    @Override
    public void publishControl(String targetServerId, ControlCommand command) {
        if (!serverId.equals(targetServerId)) return;
        for (Consumer<ControlCommand> h : controlHandlers) {
            try {
                h.accept(command);
            } catch (RuntimeException e) {
                ERROR_TRACKER.trackError(e);
            }
        }
    }

    @Override
    public void subscribeControl(Consumer<ControlCommand> handler) {
        controlHandlers.add(handler);
    }

    @Override
    public void publishEvent(CoordinationEvent event) {
        for (Consumer<CoordinationEvent> h : eventHandlers) {
            try {
                h.accept(event);
            } catch (RuntimeException e) {
                ERROR_TRACKER.trackError(e);
            }
        }
    }

    @Override
    public void subscribeEvents(Consumer<CoordinationEvent> handler) {
        eventHandlers.add(handler);
    }

    @Override
    public boolean tryAcquireMatchmakerLock() {
        return true; // single JVM -> always leader
    }

    @Override
    public void releaseMatchmakerLock() {
        // no-op
    }

    @Override
    public void close() {
        arenas.clear();
        queue.clear();
        controlHandlers.clear();
        eventHandlers.clear();
    }
}
