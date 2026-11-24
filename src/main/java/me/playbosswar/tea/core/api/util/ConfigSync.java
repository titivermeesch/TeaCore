package me.playbosswar.tea.core.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Adds keys from a plugin's bundled YAML resource that are missing from the
 * on-disk file, so new options from a plugin update reach an existing
 * {@code config.yml}. Call it early in {@code onEnable()}.
 *
 * <ul>
 *   <li>Lists and maps count as single values; only nested sections are
 *       walked.</li>
 *   <li>Values already set on disk are never changed.</li>
 *   <li>Comments of added keys are copied on 1.18.1+ only.</li>
 *   <li>No-op if the on-disk file does not exist; call
 *       {@link Plugin#saveDefaultConfig()} first.</li>
 *   <li>Added keys go at the end of their parent section, not at their
 *       position in the bundled file.</li>
 * </ul>
 */
public final class ConfigSync {

    private ConfigSync() {
    }

    /**
     * Merges the bundled {@code config.yml} into the data folder's
     * {@code config.yml}.
     *
     * @return the number of keys added
     */
    public static int mergeMissingKeys(Plugin plugin) {
        return mergeMissingKeys(plugin, "config.yml",
                plugin.getDataFolder().toPath().resolve("config.yml"));
    }

    /**
     * @param resourcePath path of the bundled YAML inside the jar, e.g.
     *                     {@code "config.yml"}
     * @return the number of keys added
     */
    public static int mergeMissingKeys(Plugin plugin, String resourcePath, Path onDisk) {
        if (!Files.isRegularFile(onDisk)) {
            return 0;
        }
        YamlConfiguration jarYaml;
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null)
                return 0;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                jarYaml = YamlConfiguration.loadConfiguration(r);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to read bundled resource " + resourcePath + " during config sync", e);
            return 0;
        }
        YamlConfiguration diskYaml = YamlConfiguration.loadConfiguration(onDisk.toFile());

        List<String> leafPaths = new ArrayList<>();
        collectLeafPaths(jarYaml, "", leafPaths);

        int added = 0;
        for (String path : leafPaths) {
            if (diskYaml.isSet(path))
                continue;
            diskYaml.set(path, jarYaml.get(path));
            tryCopyComments(jarYaml, diskYaml, path);
            added++;
        }
        if (added == 0)
            return 0;
        try {
            diskYaml.save(onDisk.toFile());
            plugin.getLogger().info("Synced " + added + " missing config key(s) to "
                    + onDisk.getFileName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to write merged config to " + onDisk, e);
        }
        return added;
    }

    private static void collectLeafPaths(ConfigurationSection section, String prefix, List<String> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                collectLeafPaths(child, path, out);
            } else {
                out.add(path);
            }
        }
    }

    private static void tryCopyComments(YamlConfiguration source, YamlConfiguration target, String path) {
        // The comment API only exists from 1.18, so it is called reflectively.
        if (COMMENTS == null) {
            return;
        }
        try {
            copy(source, target, path, COMMENTS[0], COMMENTS[1]);
            copy(source, target, path, COMMENTS[2], COMMENTS[3]);
        } catch (ReflectiveOperationException | UnsupportedOperationException ignored) {
            // values still merge
        }
    }

    private static final java.lang.reflect.Method[] COMMENTS = commentMethods();

    private static java.lang.reflect.Method[] commentMethods() {
        try {
            Class<?> c = org.bukkit.configuration.ConfigurationSection.class;
            return new java.lang.reflect.Method[] {
                c.getMethod("getComments", String.class), c.getMethod("setComments", String.class, List.class),
                c.getMethod("getInlineComments", String.class), c.getMethod("setInlineComments", String.class, List.class)};
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void copy(YamlConfiguration source, YamlConfiguration target, String path,
                             java.lang.reflect.Method get, java.lang.reflect.Method set) throws ReflectiveOperationException {
        List<String> lines = (List<String>) get.invoke(source, path);
        if (lines != null && !lines.isEmpty()) {
            set.invoke(target, path, lines);
        }
    }
}
