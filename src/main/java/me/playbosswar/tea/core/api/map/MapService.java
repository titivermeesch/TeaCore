package me.playbosswar.tea.core.api.map;

import java.util.Collection;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.World;

/**
 * Registry of {@link MapDefinition}s. The arena manager calls
 * {@link #paste(MapDefinition, World)} when it allocates an arena world.
 */
public interface MapService {

    /** Replaces any map already registered with the same {@code (gameId, mapId)}. */
    void register(MapDefinition definition);

    /** No-op if the map is not registered. */
    void unregister(GameId gameId, String mapId);

    Optional<MapDefinition> get(GameId gameId, String mapId);

    /** @return a snapshot */
    Collection<MapDefinition> forGame(GameId gameId);

    /** @return a snapshot */
    Collection<MapDefinition> all();

    MapExtensionRegistry extensions();

    /**
     * Pastes the map's schematic into the world, synchronously on the main
     * thread. Call it before teleporting players in. No-op if the map has no
     * schematic.
     */
    void paste(MapDefinition definition, World world);
}
