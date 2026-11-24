package me.playbosswar.tea.core.api.arena;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.World;

/**
 * A running arena with its own world. Most values are only meaningful until
 * the state is {@link ArenaState#RELEASED}.
 */
public interface Arena {

    ArenaId id();

    GameId gameId();

    /** @return empty for a void-only arena or before map selection has run */
    Optional<String> mapId();

    /**
     * Never changes: arenas are not reused, so each match gets a new arena.
     * Safe as a persistent key.
     */
    UUID matchId();

    ArenaState state();

    /** @return empty during {@link ArenaState#ALLOCATING} and after {@link ArenaState#RELEASED} */
    Optional<World> world();

    /** Live view that changes as players join and leave; copy it before iterating if that matters. */
    Set<UUID> players();

    /** Spectators do not play but do receive arena chat. */
    Set<UUID> spectators();

    /** @return at least 1 */
    int minPlayers();

    /** @return at least {@link #minPlayers()} */
    int maxPlayers();

    /** @return seconds left in the current phase, or -1 if it has no fixed duration */
    int phaseSecondsRemaining();
}
