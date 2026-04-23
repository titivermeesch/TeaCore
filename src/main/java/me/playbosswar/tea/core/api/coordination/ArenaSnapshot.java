package me.playbosswar.tea.core.api.coordination;

import java.time.Instant;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * A snapshot of one arena's state published by the server that owns it.
 *
 * <p>Matchmakers read these via {@link CoordinationBackend#allArenas()} to decide where
 * to route queued players. Snapshots carry only publicly-interesting coordination fields -
 * gameplay state stays on the owning server.
 *
 * @param serverId  backend server hosting the arena (e.g. {@code "arena-1"}); never {@code null}.
 * @param arenaId   id of the arena on the owning server; never {@code null}.
 * @param gameId    id of the game running in the arena; never {@code null}.
 * @param mapId     selected map id, or the empty string for a void-only arena with no map;
 *                  never {@code null}.
 * @param state     current lifecycle state of the arena; never {@code null}.
 * @param players   current participant count.
 * @param minPlayers minimum player count needed to start a match on this arena's map.
 * @param maxPlayers hard cap on participants for this arena's map.
 * @param updatedAt timestamp this snapshot was produced; older snapshots may be stale and
 *                  are expired by the backend.
 */
public record ArenaSnapshot(
        String serverId,
        ArenaId arenaId,
        GameId gameId,
        String mapId,
        ArenaState state,
        int players,
        int minPlayers,
        int maxPlayers,
        Instant updatedAt) {

    /**
     * Indicates whether this arena is currently joinable by a queued player.
     *
     * <p>An arena is available only while in {@link ArenaState#WAITING} or
     * {@link ArenaState#COUNTDOWN} and below {@link #maxPlayers()} occupants.
     *
     * @return {@code true} if the matchmaker may route a new player here.
     */
    public boolean isAvailable() {
        if (state != ArenaState.WAITING && state != ArenaState.COUNTDOWN) return false;
        return players < maxPlayers;
    }
}
