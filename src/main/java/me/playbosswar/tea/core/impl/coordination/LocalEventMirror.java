package me.playbosswar.tea.core.impl.coordination;

import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Mirrors local Bukkit lifecycle events into the distributed coordination
 * channel so every TeaCore instance on the network sees them.
 *
 * <p>
 * Registered on every backend (lobby and arena alike). On the in-memory
 * backend the published event is dispatched synchronously to local
 * subscribers; on Redis it travels through {@code tea:events} pub/sub to the
 * lobby where the dashboard WebSocket picks it up.
 */
public final class LocalEventMirror implements Listener {

    private final CoordinationBackend backend;

    public LocalEventMirror(CoordinationBackend backend) {
        this.backend = backend;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaState(ArenaStateChangeEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.ARENA_STATE,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                event.from().name(),
                event.to().name(),
                null, null, null, null, null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ArenaPlayerJoinEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.ARENA_PLAYER_JOIN,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null,
                event.player().getUniqueId().toString(),
                event.player().getName(),
                event.spectator(),
                null, null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.ARENA_PLAYER_LEAVE,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null,
                event.player().getUniqueId().toString(),
                event.player().getName(),
                null,
                event.reason().name(),
                null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.MATCH_END,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null, null, null, null, null,
                event.winner().map(java.util.UUID::toString).orElse(null),
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining()));
    }
}
