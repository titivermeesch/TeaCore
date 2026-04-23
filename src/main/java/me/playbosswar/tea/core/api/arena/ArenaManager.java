package me.playbosswar.tea.core.api.arena;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.MapSelectionPolicy;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Allocates, tracks, and releases {@link Arena} instances on this server.
 *
 * <p>Implementations create a dedicated world per arena, run the lifecycle state machine, and
 * broadcast {@link ArenaStateChangeEvent}, {@link ArenaPlayerJoinEvent},
 * {@link ArenaPlayerLeaveEvent}, {@link MatchEndEvent} and {@link MatchTimedOutEvent} as the
 * arena progresses.
 */
public interface ArenaManager {

    /**
     * Allocates a new arena for the given game.
     *
     * <p>The arena starts in {@link ArenaState#ALLOCATING}, creates its world, has its map
     * picked (forced or by the game's {@link MapSelectionPolicy}), pastes the schematic if any,
     * then transitions to {@link ArenaState#WAITING}. Fires an {@link ArenaStateChangeEvent}
     * for each transition.
     *
     * @param gameId       game to allocate for; must be registered.
     * @param forcedMapId  specific map to use, or {@code null} to let the game's
     *                     {@link MapSelectionPolicy} choose.
     * @return the newly allocated arena, never {@code null}.
     * @throws IllegalStateException    if {@code gameId} is not registered or no valid map is
     *                                  available for the game.
     * @throws IllegalArgumentException if {@code forcedMapId} is supplied but unknown for
     *                                  {@code gameId}.
     */
    Arena allocate(GameId gameId, @Nullable String forcedMapId);

    /**
     * Allocates an arena and lets the game's policy pick the map.
     *
     * <p>Equivalent to {@link #allocate(GameId, String) allocate(gameId, null)}.
     *
     * @param gameId game to allocate for; must be registered.
     * @return the newly allocated arena, never {@code null}.
     */
    default Arena allocate(GameId gameId) {
        return allocate(gameId, null);
    }

    /**
     * Releases an arena and tears down its world.
     *
     * <p>Teleports any remaining occupants out via the normal leave flow, unloads the
     * per-arena world, and deletes its folder. Safe to call on an unknown id (no-op). Fires
     * a final {@link ArenaStateChangeEvent} into {@link ArenaState#RELEASED}.
     *
     * @param id arena id to release; unknown ids are ignored.
     */
    void release(ArenaId id);

    /**
     * Looks up an arena by id.
     *
     * @param id arena id to resolve.
     * @return the arena, or empty if no arena with that id is currently tracked.
     */
    Optional<Arena> get(ArenaId id);

    /**
     * Returns the arena a player is currently inside.
     *
     * @param playerUuid uuid of the player to resolve.
     * @return the hosting arena, or empty if the player is not in any arena.
     */
    Optional<Arena> arenaOf(UUID playerUuid);

    /**
     * Returns every arena currently tracked by this manager.
     *
     * @return a snapshot collection of all arenas, never {@code null}.
     */
    Collection<Arena> all();

    /**
     * Adds a player to an arena as a participant or a spectator.
     *
     * <p>Teleports the player into the arena world, updates participant/spectator sets, and
     * fires an {@link ArenaPlayerJoinEvent}. Refuses to add the player if they are already in
     * another arena or the arena is not accepting joins.
     *
     * @param id           arena id to join.
     * @param player       player to add; must not be {@code null}.
     * @param asSpectator  {@code true} to join as spectator, {@code false} to join as participant.
     * @return {@code true} if the player was added, {@code false} if the join was rejected.
     */
    boolean addPlayer(ArenaId id, Player player, boolean asSpectator);

    /**
     * Removes a player from whatever arena they are currently in.
     *
     * <p>Teleports them out via the lobby flow, clears their arena membership, and fires an
     * {@link ArenaPlayerLeaveEvent} with the given reason. No-op if the player is not in any
     * arena.
     *
     * @param player player to remove; must not be {@code null}.
     * @param reason reason for the departure; must not be {@code null}.
     * @return {@code true} if the player was in an arena and was removed, {@code false} otherwise.
     */
    boolean removePlayer(Player player, ArenaPlayerLeaveEvent.LeaveReason reason);

    /**
     * Ends the match currently hosted in an arena.
     *
     * <p>Transitions the arena to {@link ArenaState#ENDING} with the configured
     * ending-duration and fires a {@link MatchEndEvent}. The lifecycle ticker then resets or
     * shuts down the arena according to the game's {@code EndMode}.
     *
     * @param id          arena id whose match is ending; unknown ids are no-ops.
     * @param winnerUuid  uuid of the winning player, or {@code null} for a draw or no winner.
     */
    void endMatch(ArenaId id, @Nullable UUID winnerUuid);

    /**
     * Teleports an admin into an arena as a free-moving observer.
     *
     * <p>The observer is not tracked in the arena's participant or spectator sets and is not
     * evacuated on match end, reset, or release. Useful for staff watching a live match
     * without joining it.
     *
     * @param id     arena id to observe.
     * @param player player to place into observer mode.
     * @return {@code true} if the teleport succeeded, {@code false} if the arena is unknown
     *         or has no world yet.
     */
    boolean observe(ArenaId id, Player player);

    /**
     * Returns the absolute world locations of the arena's participant spawns.
     *
     * <p>Each location is the configured {@link SpawnPoint} offset added to the arena world's
     * paste origin. Returns an empty list if the arena has no world or no map.
     *
     * @param arena arena to resolve spawns for; must not be {@code null}.
     * @return one location per configured participant spawn; never {@code null}.
     */
    List<Location> participantSpawnLocations(Arena arena);

    /**
     * Collapses the waiting/countdown timer so the next lifecycle tick advances the state.
     *
     * <p>Intended for admin use so a match can be kicked off without waiting for the full
     * countdown. No-op if the arena is not in {@link ArenaState#WAITING} or
     * {@link ArenaState#COUNTDOWN}.
     *
     * @param id arena id to fast-forward; unknown ids are ignored.
     */
    void forceStart(ArenaId id);
}
