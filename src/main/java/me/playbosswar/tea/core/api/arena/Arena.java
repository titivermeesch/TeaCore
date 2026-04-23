package me.playbosswar.tea.core.api.arena;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.World;

/**
 * Runtime view of a single arena managed by {@link ArenaManager}.
 *
 * <p>
 * An arena owns a dedicated Bukkit world, a set of participants and spectators,
 * and a
 * lifecycle state. Most fields are valid only while {@link #state()} is not
 * {@link ArenaState#RELEASED}.
 */
public interface Arena {

    /**
     * Returns the stable identifier for this arena instance.
     *
     * @return the arena id, never {@code null}.
     */
    ArenaId id();

    /**
     * Returns the id of the game this arena hosts.
     *
     * @return the game id, never {@code null}.
     */
    GameId gameId();

    /**
     * Returns the selected map id, if one has been chosen.
     *
     * @return the map id, or empty if the arena has no map (void-only) or
     *         map selection has not yet run.
     */
    Optional<String> mapId();

    /**
     * Returns the id of the match currently hosted in this arena.
     *
     * <p>
     * Stable for the arena's entire lifetime: arenas are released rather than
     * reused,
     * so the pool creates a fresh arena (with a fresh match id) for each new match.
     * Safe to use as a persistent key for stats, logs, or replay files.
     *
     * @return the match id, never {@code null}.
     */
    UUID matchId();

    /**
     * Returns the current lifecycle state.
     *
     * @return the state, never {@code null}.
     */
    ArenaState state();

    /**
     * Returns the Bukkit world dedicated to this arena.
     *
     * @return the world, or empty during {@link ArenaState#ALLOCATING} and after
     *         {@link ArenaState#RELEASED}.
     */
    Optional<World> world();

    /**
     * Returns the UUIDs of active participants.
     *
     * <p>
     * The returned set reflects the arena's live membership; callers should not
     * rely on
     * it being immutable. Iterate defensively if the arena may mutate concurrently.
     *
     * @return a set of player UUIDs, never {@code null}.
     */
    Set<UUID> players();

    /**
     * Returns the UUIDs of active spectators.
     *
     * <p>
     * Spectators join with {@code asSpectator=true} via
     * {@link ArenaManager#addPlayer(ArenaId, org.bukkit.entity.Player, boolean)}
     * and do not
     * participate in gameplay, but they count as arena occupants for chat routing.
     *
     * @return a set of spectator UUIDs, never {@code null}.
     */
    Set<UUID> spectators();

    /**
     * Returns the minimum participant count needed to start a match on this arena's
     * map.
     *
     * @return the minimum, at least {@code 1}.
     */
    int minPlayers();

    /**
     * Returns the maximum participant count allowed on this arena's map.
     *
     * @return the maximum, at least {@link #minPlayers()}.
     */
    int maxPlayers();

    /**
     * Returns the seconds remaining in the current lifecycle phase.
     *
     * @return seconds left, or {@code -1} if the current phase has no fixed
     *         duration.
     */
    int phaseSecondsRemaining();
}
