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
 * Merges missing keys from a plugin's bundled YAML resource into the matching
 * on-disk file.
 *
 * <p>
 * Bukkit's built-in {@code saveDefaultConfig() / copyDefaults} only kicks in
 * when the disk file is absent or for top-level scalars; once admins have
 * customized their {@code config.yml}, new keys shipped in a plugin update
 * never reach disk. Game plugins that ship a default {@code config.yml} should
 * call this method early in {@code onEnable()} to surface newly-added knobs
 * without touching anything the admin already set.
 *
 * <p>
 * <b>Semantics:</b>
 * <ul>
 *   <li>Walks every leaf path in the bundled resource (lists/maps count as
 *       leaves; the walker only descends into nested {@link ConfigurationSection
 *       sections}).</li>
 *   <li>Adds a path to the on-disk file only when {@code isSet} returns
 *       {@code false} - existing values, including ones the admin set to a
 *       different value, are preserved verbatim.</li>
 *   <li>Best-effort: comments attached to newly-added keys are copied across
 *       on Bukkit/Paper builds that expose {@code getComments / setComments}
 *       (1.18.1+). Older runtimes silently skip the comment carryover and
 *       still merge the values.</li>
 *   <li>If the on-disk file does not exist yet, this method is a no-op -
 *       {@link Plugin#saveDefaultConfig()} should be called first to plant
 *       the bundled file.</li>
 * </ul>
 *
 * <p>
 * <b>Limitations:</b> newly-added keys land at the end of their parent
 * section rather than at the position they occupy in the bundled resource;
 * Bukkit's {@code YamlConfiguration} writes in insertion order, and the
 * admin's existing keys were inserted first. Reorganizing the file by hand
 * is always safe.
 */
public final class ConfigSync {

    private ConfigSync() {
    }

    /**
     * Convenience overload that targets the plugin's standard
     * {@code config.yml} ({@link Plugin#getDataFolder()}/config.yml against
     * the bundled {@code config.yml} resource).
     *
     * @param plugin plugin whose resource and data folder to use; must not be
     *               {@code null}.
     * @return the number of keys added; 0 if everything was already present
     *         or the disk file does not exist yet.
     */
    public static int mergeMissingKeys(Plugin plugin) {
        return mergeMissingKeys(plugin, "config.yml",
                plugin.getDataFolder().toPath().resolve("config.yml"));
    }

    /**
     * Copies any keys that exist in the bundled {@code resourcePath} but are
     * missing from the on-disk file, preserving every value the admin already
     * authored.
     *
     * @param plugin       plugin whose jar to read the resource from; must
     *                     not be {@code null}.
     * @param resourcePath classpath path of the bundled YAML inside the jar
     *                     (e.g. {@code "config.yml"}); must not be
     *                     {@code null}.
     * @param onDisk       absolute path to the live YAML file admins edit;
     *                     must not be {@code null}.
     * @return the number of keys added; 0 if everything was already present
     *         or {@code onDisk} does not exist yet.
     */
    public static int mergeMissingKeys(Plugin plugin, String resourcePath, Path onDisk) {
        if (!Files.isRegularFile(onDisk)) {
            // Nothing to merge into; saveDefaultConfig() (or saveResource())
            // is responsible for planting the bundled file initially.
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

    /**
     * Walks the section tree depth-first, appending every leaf path (i.e.
     * paths whose value is not itself a section) to {@code out}. Lists and
     * maps stored under a single key count as leaves and are not descended
     * into.
     */
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

    /**
     * Best-effort comment carry-through. Older Bukkit forks do not expose
     * {@code getComments / setComments}, so we swallow {@link
     * NoSuchMethodError} and {@link UnsupportedOperationException} rather
     * than failing the whole merge.
     */
    private static void tryCopyComments(YamlConfiguration source, YamlConfiguration target, String path) {
        try {
            List<String> header = source.getComments(path);
            if (header != null && !header.isEmpty()) {
                target.setComments(path, header);
            }
            List<String> inline = source.getInlineComments(path);
            if (inline != null && !inline.isEmpty()) {
                target.setInlineComments(path, inline);
            }
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            // Older API: silently skip; values still merge.
        }
    }
}
