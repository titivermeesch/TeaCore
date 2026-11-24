package me.playbosswar.tea.core.api.arena;

import me.playbosswar.tea.core.api.game.MapSelectionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Allocates, tracks and releases this server's {@link Arena}s. Each arena gets
 * its own world and fires {@link ArenaStateChangeEvent},
 * {@link ArenaPlayerJoinEvent}, {@link ArenaPlayerLeaveEvent},
 * {@link MatchEndEvent} and {@link MatchTimedOutEvent} as it progresses.
 */
public interface ArenaManager {

    /**
     * Creates the arena world, picks the map (forced, or by the game's
     * {@link MapSelectionPolicy}), pastes the schematic and moves from
     * {@link ArenaState#ALLOCATING} to {@link ArenaState#WAITING}, firing an
     * {@link ArenaStateChangeEvent} per transition.
     *
     * @throws IllegalStateException if {@code gameId} is not registered or has no valid map
     * @throws IllegalArgumentException if {@code forcedMapId} is unknown for {@code gameId}
     */
    Arena allocate(GameId gameId, @Nullable String forcedMapId);

    default Arena allocate(GameId gameId) {
        return allocate(gameId, null);
    }

    /**
     * Evacuates remaining occupants through the normal leave flow, unloads and
     * deletes the arena world, and fires the final transition to
     * {@link ArenaState#RELEASED}. No-op for unknown ids.
     */
    void release(ArenaId id);

    Optional<Arena> get(ArenaId id);

    Optional<Arena> arenaOf(UUID playerUuid);

    Collection<Arena> all();

    /**
     * Teleports the player in and fires {@link ArenaPlayerJoinEvent}.
     *
     * @return {@code false} if the player is already in another arena or the arena is not accepting joins
     */
    boolean addPlayer(ArenaId id, Player player, boolean asSpectator);

    /**
     * Teleports the player out through the lobby flow and fires
     * {@link ArenaPlayerLeaveEvent}.
     *
     * @return {@code false} if the player was not in an arena
     */
    boolean removePlayer(Player player, ArenaPlayerLeaveEvent.LeaveReason reason);

    /**
     * Moves the arena to {@link ArenaState#ENDING} and fires {@link MatchEndEvent};
     * the game's {@code EndMode} then decides whether it resets or shuts down.
     * No-op for unknown ids.
     *
     * @param winnerUuid {@code null} for a draw or no winner
     */
    void endMatch(ArenaId id, @Nullable UUID winnerUuid);

    /**
     * Like {@link #endMatch(ArenaId, UUID)}, but every member of
     * {@code winningTeam} is credited with the win. {@link MatchEndEvent#winner()}
     * is one representative member.
     *
     * @param winningTeam {@code null} for a draw or no winner
     */
    void endMatchForTeam(ArenaId id, @Nullable me.playbosswar.tea.core.api.team.TeamId winningTeam);

    /**
     * Teleports a staff member in as an untracked observer: not a participant or
     * spectator, and not evacuated on match end, reset or release.
     *
     * @return {@code false} if the arena is unknown or has no world yet
     */
    boolean observe(ArenaId id, Player player);

    /**
     * Participant spawn offsets added to the arena's paste origin. Empty if the
     * arena has no world or map.
     */
    List<Location> participantSpawnLocations(Arena arena);

    /**
     * Skips the rest of the waiting or countdown timer so the next tick advances
     * the state. No-op unless the arena is {@link ArenaState#WAITING} or
     * {@link ArenaState#COUNTDOWN}.
     */
    void forceStart(ArenaId id);

    /**
     * Marks the arena world as modified (blocks placed or broken) so it is
     * rebuilt from the schematic instead of reused as a spare. Cleared on release.
     *
     * @since 0.2.0
     */
    default void markWorldChanged(ArenaId id) {
    }
}
