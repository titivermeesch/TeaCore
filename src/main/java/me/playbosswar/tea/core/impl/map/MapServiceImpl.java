package me.playbosswar.tea.core.impl.map;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.map.MapExtensionRegistry;
import me.playbosswar.tea.core.api.map.MapService;
import org.bukkit.World;

public final class MapServiceImpl implements MapService {

    private final Logger logger;
    private final MapExtensionRegistryImpl extensions;
    private final ConcurrentHashMap<GameId, ConcurrentHashMap<String, MapDefinition>> maps = new ConcurrentHashMap<>();

    public MapServiceImpl(Logger logger) {
        this.logger = logger;
        this.extensions = new MapExtensionRegistryImpl();
    }

    @Override
    public MapExtensionRegistry extensions() {
        return extensions;
    }

    public MapExtensionRegistryImpl extensionRegistry() {
        return extensions;
    }

    @Override
    public void register(MapDefinition definition) {
        maps.computeIfAbsent(definition.gameId(), g -> new ConcurrentHashMap<>())
                .put(definition.mapId(), definition);
        logger.fine(() -> "Registered map " + definition.gameId() + "/" + definition.mapId());
    }

    @Override
    public void unregister(GameId gameId, String mapId) {
        ConcurrentHashMap<String, MapDefinition> forGame = maps.get(gameId);
        if (forGame != null)
            forGame.remove(mapId);
    }

    @Override
    public Optional<MapDefinition> get(GameId gameId, String mapId) {
        ConcurrentHashMap<String, MapDefinition> forGame = maps.get(gameId);
        return forGame == null ? Optional.empty() : Optional.ofNullable(forGame.get(mapId));
    }

    @Override
    public Collection<MapDefinition> forGame(GameId gameId) {
        ConcurrentHashMap<String, MapDefinition> forGame = maps.get(gameId);
        return forGame == null ? Collections.emptyList() : Collections.unmodifiableCollection(forGame.values());
    }

    @Override
    public Collection<MapDefinition> all() {
        java.util.List<MapDefinition> out = new java.util.ArrayList<>();
        for (ConcurrentHashMap<String, MapDefinition> perGame : maps.values()) {
            out.addAll(perGame.values());
        }
        return java.util.Collections.unmodifiableCollection(out);
    }

    @Override
    public void paste(MapDefinition definition, World world) {
        if (definition.schematic().isEmpty())
            return;
        SchematicStore store = SchematicStore.current();
        if (!store.available()) {
            logger.warning("Skipping paste for " + definition.mapId() + ": " + store.unavailableReason()
                    + " Arenas will use empty void worlds.");
            return;
        }
        try {
            store.paste(definition.schematicFile(), world, 0, 64, 0);
            logger.info("Pasted map " + definition.mapId() + " into " + world.getName());
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to paste map " + definition.mapId(), t);
        }
    }

}
