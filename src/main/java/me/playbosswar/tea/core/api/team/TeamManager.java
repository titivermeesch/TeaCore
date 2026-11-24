package me.playbosswar.tea.core.api.team;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.ArenaId;

/**
 * Assigns players to {@link Team}s within an arena. Team state is per arena and
 * is cleared when the arena resets or is released.
 */
public interface TeamManager {

    /**
     * Picks a team using the game's {@link me.playbosswar.tea.core.api.game.TeamMode}
     * and each team's {@link TeamDefinition#maxSize()}. A player who is already
     * assigned keeps their current team.
     *
     * @throws IllegalStateException if no team has capacity.
     */
    Team assign(ArenaId arenaId, UUID playerUuid);

    Optional<Team> teamOf(ArenaId arenaId, UUID playerUuid);

    /** Returns a snapshot. */
    Collection<Team> teamsFor(ArenaId arenaId);

    /** No-op if the player has no team in this arena. */
    void remove(ArenaId arenaId, UUID playerUuid);

    /** Clears all team state. The arena manager calls this on reset and release. */
    void resetArena(ArenaId arenaId);

    /** {@code true} for FFA games, or games that declare exactly one team. */
    boolean isSingleTeam(ArenaId arenaId);

    /**
     * Uses only the first {@code count} of the game's declared teams in this
     * arena, e.g. when its map has bases for fewer teams than the game allows.
     * Call before the first {@link #assign}; {@link #resetArena} clears it.
     *
     * @since 0.2.0
     */
    default void limitTeams(ArenaId arenaId, int count) {
    }
}
