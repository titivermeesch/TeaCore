package me.playbosswar.tea.core.impl.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.map.MapExtension;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads and writes per-game map YAML files. Layout:
 *
 * <pre>{@code
 *   plugins/TeaCore/maps/<gameId>.yml
 *   plugins/TeaCore/maps/schems/<filename>.schem
 * }</pre>
 *
 * Spawn coordinates are stored as absolute world coordinates captured from the
 * admin's location at authoring time. When pasting a schematic into an arena,
 * the admin is responsible for building at (0, 64, 0) so the spawns line up -
 * see the doc milestone for the authoring workflow.
 */
public final class MapPersistence {

    private final Path mapsBaseDir;
    private final Path schematicsDir;
    private final Logger logger;
    private final MapExtensionRegistryImpl extensionRegistry;

    public MapPersistence(Path mapsBaseDir, Logger logger, MapExtensionRegistryImpl extensionRegistry) {
        this.mapsBaseDir = mapsBaseDir;
        this.schematicsDir = mapsBaseDir.resolve("schems");
        this.logger = logger;
        this.extensionRegistry = extensionRegistry;
    }

    public Path mapsBaseDir() {
        return mapsBaseDir;
    }

    public Path schematicsDir() {
        return schematicsDir;
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(schematicsDir);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create maps directory " + schematicsDir, e);
        }
    }

    public List<MapDefinition> loadAll() {
        ensureDirectories();
        List<MapDefinition> out = new ArrayList<>();
        try (var stream = Files.list(mapsBaseDir)) {
            for (Path path : stream.toList()) {
                if (!Files.isRegularFile(path))
                    continue;
                String name = path.getFileName().toString();
                if (!name.endsWith(".yml"))
                    continue;
                String gameIdStr = name.substring(0, name.length() - 4);
                try {
                    out.addAll(loadForGame(new GameId(gameIdStr)));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to load maps for " + gameIdStr, e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list maps dir " + mapsBaseDir, e);
        }
        return out;
    }

    public List<MapDefinition> loadForGame(GameId gameId) {
        Path file = mapsBaseDir.resolve(gameId.value() + ".yml");
        if (!Files.isRegularFile(file))
            return List.of();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection maps = yaml.getConfigurationSection("maps");
        if (maps == null)
            return List.of();

        List<MapDefinition> out = new ArrayList<>();
        for (String mapId : maps.getKeys(false)) {
            ConfigurationSection mapSection = maps.getConfigurationSection(mapId);
            if (mapSection == null)
                continue;

            String schematicName = mapSection.getString("schematic-file");
            Path schematicPath = schematicName == null ? null : schematicsDir.resolve(schematicName);

            SpawnPoint waiting = readSpawn(mapSection.getConfigurationSection("waiting-spawn"), "waiting");

            List<SpawnPoint> participants = new ArrayList<>();
            List<?> raw = mapSection.getList("participant-spawns");
            if (raw != null) {
                int i = 0;
                for (Object entry : raw) {
                    if (!(entry instanceof Map<?, ?> m))
                        continue;
                    participants.add(readSpawnMap(m, "s" + i));
                    i++;
                }
            }

            Map<String, Object> extensions = new LinkedHashMap<>();
            ConfigurationSection extensionsSection = mapSection.getConfigurationSection("extensions");
            if (extensionsSection != null) {
                for (String id : extensionsSection.getKeys(false)) {
                    ConfigurationSection entrySection = extensionsSection.getConfigurationSection(id);
                    if (entrySection == null)
                        continue;
                    MapExtension<?> ext = extensionRegistry.byId(id).orElse(null);
                    if (ext == null) {
                        logger.warning("Map " + gameId + "/" + mapId + " references unregistered extension '"
                                + id + "' (is the owning plugin loaded?). Skipping entry.");
                        continue;
                    }
                    try {
                        Object value = ext.fromYaml(entrySection);
                        if (value != null)
                            extensions.put(id, value);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to load extension '" + id
                                + "' on " + gameId + "/" + mapId, e);
                    }
                }
            }

            int minPlayers = mapSection.getInt("min-players", 2);
            int maxPlayers = mapSection.getInt("max-players", 16);

            out.add(new MapDefinition(gameId, mapId, schematicPath, waiting, participants,
                    extensions, minPlayers, maxPlayers));
        }
        return out;
    }

    public void save(MapDefinition def) throws IOException {
        ensureDirectories();
        GameId gameId = def.gameId();
        Path file = mapsBaseDir.resolve(gameId.value() + ".yml");

        YamlConfiguration yaml = Files.isRegularFile(file)
                ? YamlConfiguration.loadConfiguration(file.toFile())
                : new YamlConfiguration();

        ConfigurationSection maps = yaml.getConfigurationSection("maps");
        if (maps == null)
            maps = yaml.createSection("maps");

        ConfigurationSection section = maps.createSection(def.mapId());
        section.set("min-players", def.minPlayers());
        section.set("max-players", def.maxPlayers());
        def.schematic().ifPresent(p -> section.set("schematic-file",
                schematicsDir.relativize(p).toString().replace('\\', '/')));
        if (def.waitingSpawn() != null) {
            section.set("waiting-spawn", spawnMap(def.waitingSpawn()));
        }
        if (!def.participantSpawns().isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (SpawnPoint s : def.participantSpawns())
                list.add(spawnMap(s));
            section.set("participant-spawns", list);
        }
        if (!def.extensions().isEmpty()) {
            ConfigurationSection extensionsSection = section.createSection("extensions");
            for (Map.Entry<String, Object> entry : def.extensions().entrySet()) {
                String id = entry.getKey();
                MapExtension<?> ext = extensionRegistry.byId(id).orElse(null);
                if (ext == null) {
                    logger.warning("Cannot persist extension '" + id + "' on " + def.gameId()
                            + "/" + def.mapId() + "; no registration found.");
                    continue;
                }
                try {
                    writeExtension(ext, entry.getValue(), extensionsSection.createSection(id));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to save extension '" + id
                            + "' on " + def.gameId() + "/" + def.mapId(), e);
                }
            }
        }

        yaml.save(file.toFile());
    }

    public void delete(GameId gameId, String mapId) throws IOException {
        Path file = mapsBaseDir.resolve(gameId.value() + ".yml");
        if (!Files.isRegularFile(file))
            return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection maps = yaml.getConfigurationSection("maps");
        if (maps == null)
            return;
        maps.set(mapId, null);
        if (maps.getKeys(false).isEmpty()) {
            Files.deleteIfExists(file);
        } else {
            yaml.save(file.toFile());
        }
    }

    private static SpawnPoint readSpawn(ConfigurationSection section, String fallbackId) {
        if (section == null)
            return null;
        return new SpawnPoint(
                section.getString("id", fallbackId),
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    private static SpawnPoint readSpawnMap(Map<?, ?> m, String fallbackId) {
        Object idObj = m.get("id");
        return new SpawnPoint(
                idObj == null ? fallbackId : idObj.toString(),
                asDouble(m.get("x"), 0),
                asDouble(m.get("y"), 64),
                asDouble(m.get("z"), 0),
                (float) asDouble(m.get("yaw"), 0),
                (float) asDouble(m.get("pitch"), 0));
    }

    private static double asDouble(Object o, double fallback) {
        if (o instanceof Number n)
            return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void writeExtension(MapExtension<?> ext, Object value, ConfigurationSection target) {
        ((MapExtension) ext).toYaml(value, target);
    }

    private static Map<String, Object> spawnMap(SpawnPoint s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id());
        out.put("x", s.x());
        out.put("y", s.y());
        out.put("z", s.z());
        out.put("yaw", s.yaw());
        out.put("pitch", s.pitch());
        return out;
    }
}
