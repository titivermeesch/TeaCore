package me.playbosswar.tea.core.impl.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class LocaleBundleLoader {

    private LocaleBundleLoader() {
    }

    /**
     * Disk values win over jar defaults. Jar keys missing on disk are still returned
     * and written back to disk so admins can find them; existing disk keys are never
     * overwritten.
     */
    public static LocaleBundle load(Plugin plugin, Path dataFolder, String locale) {
        Map<String, Object> jarRaw = new LinkedHashMap<>();
        Map<String, Object> diskRaw = new LinkedHashMap<>();

        YamlConfiguration jarYaml = null;
        try (InputStream in = plugin.getResource("lang/" + locale + ".yml")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    jarYaml = YamlConfiguration.loadConfiguration(r);
                }
            }
        } catch (IOException e) {
            Logger.getLogger(LocaleBundleLoader.class.getName())
                    .log(Level.WARNING, "Failed to read jar locale bundle " + locale, e);
        }
        if (jarYaml != null)
            flatten(jarYaml, "", jarRaw);

        Path onDisk = dataFolder.resolve("lang").resolve(locale + ".yml");
        YamlConfiguration diskYaml = null;
        if (Files.isRegularFile(onDisk)) {
            diskYaml = YamlConfiguration.loadConfiguration(onDisk.toFile());
            flatten(diskYaml, "", diskRaw);
        }

        // Keep list values as lists so multiline lore stays a YAML list on disk.
        if (diskYaml != null && jarYaml != null) {
            int added = 0;
            for (Map.Entry<String, Object> e : jarRaw.entrySet()) {
                if (!diskRaw.containsKey(e.getKey())) {
                    diskYaml.set(e.getKey(), e.getValue());
                    diskRaw.put(e.getKey(), e.getValue());
                    added++;
                }
            }
            if (added > 0) {
                try {
                    diskYaml.save(onDisk.toFile());
                    plugin.getLogger().info("Synced " + added + " missing " + locale
                            + " entries to " + onDisk.getFileName());
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to sync missing entries to " + onDisk, e);
                }
            }
        }

        Map<String, String> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : jarRaw.entrySet())
            merged.put(e.getKey(), joinLines(e.getValue()));
        for (Map.Entry<String, Object> e : diskRaw.entrySet())
            merged.put(e.getKey(), joinLines(e.getValue())); // disk wins
        return new LocaleBundle(locale, merged);
    }

    /** Lists stay {@code List<?>} so the writeback keeps their YAML shape. */
    private static void flatten(ConfigurationSection section, String prefix, Map<String, Object> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                flatten(child, path, out);
            } else if (value != null) {
                out.put(path, value);
            }
        }
    }

    private static String joinLines(Object value) {
        if (value instanceof java.util.List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0)
                    sb.append('\n');
                Object item = list.get(i);
                sb.append(item == null ? "" : item.toString());
            }
            return sb.toString();
        }
        return value.toString();
    }
}
