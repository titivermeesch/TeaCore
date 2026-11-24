package me.playbosswar.tea.core.impl.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Before a map is saved over, deleted or restored, its YAML entry and schematic are
 * copied to {@code maps/backups/<game>/<map>/<timestamp>/}. Only the newest
 * {@code maps.keep-versions} (default 10) versions per map are kept.
 */
public final class MapBackups {

    /** One stored version. {@code number} 1 is the newest. */
    public record Version(int number, Instant when, String actor, String reason, Path dir) { }

    private static final Pattern SAFE = Pattern.compile("[a-z0-9_][a-z0-9_.-]*");
    private static final DateTimeFormatter DIR_NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final MapPersistence persistence;
    private final IntSupplier keep;
    private final Logger logger;

    public MapBackups(MapPersistence persistence, IntSupplier keep, Logger logger) {
        this.persistence = persistence;
        this.keep = keep;
        this.logger = logger;
    }

    private Path mapDir(GameId game, String mapId) {
        if (!SAFE.matcher(mapId).matches() || mapId.contains("..")) {
            throw new IllegalArgumentException("Invalid map id: " + mapId);
        }
        return persistence.mapsBaseDir().resolve("backups").resolve(game.value()).resolve(mapId);
    }

    private Path gameFile(GameId game) {
        return persistence.mapsBaseDir().resolve(game.value() + ".yml");
    }

    private Path schematicOf(String mapId) {
        return persistence.schematicsDir().resolve(mapId + ".schem");
    }

    /** Does nothing for a map with no YAML entry yet. */
    public synchronized boolean snapshot(GameId game, String mapId, String actor, String reason) {
        try {
            Path file = gameFile(game);
            if (!Files.isRegularFile(file)) {
                return false;
            }
            ConfigurationSection entry = YamlConfiguration.loadConfiguration(file.toFile())
                    .getConfigurationSection("maps." + mapId);
            if (entry == null) {
                return false;
            }
            Path dir = mapDir(game, mapId).resolve(uniqueName(game, mapId));
            Files.createDirectories(dir);
            YamlConfiguration out = new YamlConfiguration();
            copy(entry, out.createSection("map"));
            out.save(dir.resolve("map.yml").toFile());
            Path schem = schematicOf(mapId);
            if (Files.isRegularFile(schem)) {
                Files.copy(schem, dir.resolve("map.schem"), StandardCopyOption.REPLACE_EXISTING);
            }
            YamlConfiguration meta = new YamlConfiguration();
            meta.set("when", Instant.now().toString());
            meta.set("actor", actor);
            meta.set("reason", reason);
            meta.save(dir.resolve("version.yml").toFile());
            prune(game, mapId);
            return true;
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not back up map " + game + "/" + mapId, e);
            return false;
        }
    }

    private String uniqueName(GameId game, String mapId) {
        String name = DIR_NAME.format(Instant.now());
        Path base = mapDir(game, mapId);
        String candidate = name;
        for (int i = 2; Files.exists(base.resolve(candidate)); i++) {
            candidate = name + "-" + i;
        }
        return candidate;
    }

    /** Stored versions, newest first. */
    public synchronized List<Version> list(GameId game, String mapId) {
        Path base = mapDir(game, mapId);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.list(base)) {
            stream.filter(p -> Files.isRegularFile(p.resolve("map.yml"))).forEach(dirs::add);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not list backups of " + game + "/" + mapId, e);
            return List.of();
        }
        dirs.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        List<Version> out = new ArrayList<>();
        for (int i = 0; i < dirs.size(); i++) {
            Path dir = dirs.get(i);
            YamlConfiguration meta = YamlConfiguration.loadConfiguration(dir.resolve("version.yml").toFile());
            Instant when;
            try {
                when = Instant.parse(meta.getString("when", ""));
            } catch (RuntimeException e) {
                when = Instant.EPOCH;
            }
            out.add(new Version(i + 1, when, meta.getString("actor", "?"), meta.getString("reason", "?"), dir));
        }
        return out;
    }

    public Optional<Version> get(GameId game, String mapId, int number) {
        return list(game, mapId).stream().filter(v -> v.number() == number).findFirst();
    }

    /** Backs up the current map first. The caller re-registers the map. */
    public synchronized void restore(GameId game, String mapId, Version version, String actor) throws IOException {
        Path dir = version.dir();
        snapshot(game, mapId, actor, "before restore of version from " + version.when());
        ConfigurationSection saved = YamlConfiguration.loadConfiguration(dir.resolve("map.yml").toFile())
                .getConfigurationSection("map");
        if (saved == null) {
            throw new IOException("Backup has no map entry: " + dir);
        }
        Path file = gameFile(game);
        YamlConfiguration yaml = Files.isRegularFile(file)
                ? YamlConfiguration.loadConfiguration(file.toFile()) : new YamlConfiguration();
        yaml.set("maps." + mapId, null);
        copy(saved, yaml.createSection("maps." + mapId));
        Path schem = dir.resolve("map.schem");
        if (Files.isRegularFile(schem)) {
            persistence.ensureDirectories();
            Files.copy(schem, schematicOf(mapId), StandardCopyOption.REPLACE_EXISTING);
        }
        yaml.save(file.toFile());
    }

    private void prune(GameId game, String mapId) {
        int max = Math.max(1, keep.getAsInt());
        List<Version> all = list(game, mapId);
        for (Version v : all.subList(Math.min(max, all.size()), all.size())) {
            try (var walk = Files.walk(v.dir())) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not prune map backup " + v.dir(), e);
            }
        }
    }

    private static void copy(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            if (from.isConfigurationSection(key)) {
                copy(from.getConfigurationSection(key), to.createSection(key));
            } else {
                to.set(key, from.get(key));
            }
        }
    }
}
