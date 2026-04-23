package me.playbosswar.tea.core.api.coordination;

import org.jetbrains.annotations.Nullable;

/**
 * A flat, serializable arena-or-match event broadcast to every backend on the
 * Tea network.
 *
 * <p>
 * Each backend mirrors its local Bukkit events
 * ({@link me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent},
 * {@link me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent}, etc.) into
 * one of these via
 * {@link CoordinationBackend#publishEvent(CoordinationEvent)}, and the lobby's
 * dashboard subscribes via
 * {@link CoordinationBackend#subscribeEvents(java.util.function.Consumer)} to
 * push a network-wide live feed to the browser. The record is intentionally
 * flat so it serializes cleanly over Redis pub/sub without custom type
 * adapters; nullable fields reflect which facts are relevant per
 * {@link Type}.
 *
 * @param type                   event discriminator; never {@code null}.
 * @param serverId               the originating backend; never {@code null}.
 * @param arenaId                arena id for arena- and match-level events.
 * @param gameId                 game id for arena- and match-level events.
 * @param fromState              previous state for {@link Type#ARENA_STATE}.
 * @param toState                new state for {@link Type#ARENA_STATE}.
 * @param playerUuid             uuid for player join/leave events.
 * @param playerName             display name for player join/leave events.
 * @param spectator              whether the player joined as a spectator
 *                               ({@link Type#ARENA_PLAYER_JOIN}).
 * @param reason                 leave reason
 *                               ({@link Type#ARENA_PLAYER_LEAVE}).
 * @param winnerUuid             winner uuid on {@link Type#MATCH_END}.
 * @param playerCount            live participant count post-event.
 * @param phaseSecondsRemaining  phase timer remaining after transition
 *                               ({@link Type#ARENA_STATE}); {@code -1} when
 *                               not applicable.
 */
public record CoordinationEvent(
        Type type,
        String serverId,
        @Nullable String arenaId,
        @Nullable String gameId,
        @Nullable String fromState,
        @Nullable String toState,
        @Nullable String playerUuid,
        @Nullable String playerName,
        @Nullable Boolean spectator,
        @Nullable String reason,
        @Nullable String winnerUuid,
        int playerCount,
        int phaseSecondsRemaining) {

    public enum Type {
        ARENA_STATE,
        ARENA_PLAYER_JOIN,
        ARENA_PLAYER_LEAVE,
        MATCH_END
    }
}
