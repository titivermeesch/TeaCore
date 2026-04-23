package me.playbosswar.tea.core.api.team;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.ArenaId;

/**
 * Assigns players to {@link Team}s within an arena and tracks their membership.
 *
 * <p>
 * The assignment algorithm consults the game's
 * {@link me.playbosswar.tea.core.api.game.TeamMode} and declared
 * {@link TeamDefinition}s. Team state is arena-scoped: resetting or releasing
 * an arena
 * clears its teams.
 */
public interface TeamManager {

    /**
     * Assigns a player to a team for the given arena, using the game's team mode.
     *
     * <p>
     * Selects a team based on the game's
     * {@link me.playbosswar.tea.core.api.game.TeamMode} and per-team
     * {@link TeamDefinition#maxSize()}. If the player is already assigned, returns
     * their
     * current team without reassigning.
     *
     * @param arenaId    arena to assign within; must not be {@code null}.
     * @param playerUuid player to assign; must not be {@code null}.
     * @return the team the player now belongs to, never {@code null}.
     * @throws IllegalStateException if no team has capacity.
     */
    Team assign(ArenaId arenaId, UUID playerUuid);

    /**
     * Returns the team a player is on in an arena, if any.
     *
     * @param arenaId    arena to inspect; must not be {@code null}.
     * @param playerUuid player to look up; must not be {@code null}.
     * @return the team, or empty if the player is not on any team in this arena.
     */
    Optional<Team> teamOf(ArenaId arenaId, UUID playerUuid);

    /**
     * Returns every team in an arena.
     *
     * @param arenaId arena to list teams for; must not be {@code null}.
     * @return a snapshot collection of teams, never {@code null}.
     */
    Collection<Team> teamsFor(ArenaId arenaId);

    /**
     * Removes a player from their team in the arena.
     *
     * <p>
     * No-op if the player is not currently assigned to any team in this arena.
     *
     * @param arenaId    arena in which to remove the assignment; must not be
     *                   {@code null}.
     * @param playerUuid player to remove; must not be {@code null}.
     */
    void remove(ArenaId arenaId, UUID playerUuid);

    /**
     * Clears all team state for an arena.
     *
     * <p>
     * Called by the arena manager on reset or release so the next match starts from
     * an
     * empty team roster.
     *
     * @param arenaId arena to clear; must not be {@code null}.
     */
    void resetArena(ArenaId arenaId);

    /**
     * Indicates whether the arena's game effectively uses a single team.
     *
     * @param arenaId arena to inspect; must not be {@code null}.
     * @return {@code true} for FFA games with one team or games with explicitly one
     *         declared
     *         team.
     */
    boolean isSingleTeam(ArenaId arenaId);
}
