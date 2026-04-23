package me.playbosswar.tea.core.api.map;

import java.util.Collection;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.World;

/**
 * Registry and loader of {@link MapDefinition}s.
 *
 * <p>
 * Games register their maps in {@code onEnable} and the arena manager calls
 * {@link #paste(MapDefinition, World)} at allocation time to stamp the
 * schematic into each
 * new arena world.
 */
public interface MapService {

    /**
     * Registers a map for a given game.
     *
     * <p>
     * Games typically do this in {@code onEnable}. Overwrites any previous
     * registration
     * for the same {@code (gameId, mapId)} pair.
     *
     * @param definition the map definition to register; must not be {@code null}.
     */
    void register(MapDefinition definition);

    /**
     * Unregisters a map.
     *
     * <p>
     * No-op if no map is registered under that id.
     *
     * @param gameId game the map belongs to; must not be {@code null}.
     * @param mapId  id of the map to remove; must not be {@code null}.
     */
    void unregister(GameId gameId, String mapId);

    /**
     * Looks up a registered map.
     *
     * @param gameId owning game; must not be {@code null}.
     * @param mapId  map id; must not be {@code null}.
     * @return the definition, or empty if not registered.
     */
    Optional<MapDefinition> get(GameId gameId, String mapId);

    /**
     * Returns every map registered for a game.
     *
     * @param gameId game whose maps to list; must not be {@code null}.
     * @return a snapshot collection, never {@code null}.
     */
    Collection<MapDefinition> forGame(GameId gameId);

    /**
     * Returns every map registered across all games.
     *
     * @return a snapshot collection, never {@code null}.
     */
    Collection<MapDefinition> all();

    /**
     * Returns the registry of game-contributed {@link MapExtension}s. Games
     * register their extensions here on {@code onEnable}.
     *
     * @return the extension registry, never {@code null}.
     */
    MapExtensionRegistry extensions();

    /**
     * Applies a map's schematic into the given world, typically a freshly-created
     * void world.
     *
     * <p>
     * No-op when the definition has no schematic. Runs synchronously on the main
     * thread
     * and mutates the world by placing blocks; callers should perform this before
     * teleporting players in.
     *
     * @param definition definition whose schematic to paste; must not be
     *                   {@code null}.
     * @param world      destination world; must not be {@code null}.
     */
    void paste(MapDefinition definition, World world);
}
