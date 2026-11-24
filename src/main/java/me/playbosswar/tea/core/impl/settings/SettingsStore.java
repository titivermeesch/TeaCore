package me.playbosswar.tea.core.impl.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.Change;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Settings staff changed, kept in the database so a server that starts later
 * gets them too. TeaCore writes them into the config files at startup, before
 * any game plugin is enabled and reads its file.
 */
public final class SettingsStore {

    private final DataSource db;
    private final Logger logger;
    private final ObjectMapper json = new ObjectMapper();

    public SettingsStore(DataSource db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /** Stores the writes of one change. A write replaces anything stored below its path. */
    public void record(String fileId, String plugin, String file, List<Change> writes, String actor) {
        try (Connection c = db.getConnection()) {
            long now = System.currentTimeMillis();
            for (Change w : writes) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM tea_settings WHERE file_id = ? AND (path = ? OR path LIKE ? ESCAPE '!' OR path LIKE ? ESCAPE '!')")) {
                    ps.setString(1, fileId);
                    ps.setString(2, w.path());
                    ps.setString(3, escape(w.path()) + ".%");
                    ps.setString(4, escape(w.path()) + "[%");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_settings "
                        + "(file_id, path, plugin, file, value, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, fileId);
                    ps.setString(2, w.path());
                    ps.setString(3, plugin);
                    ps.setString(4, file);
                    ps.setString(5, json.writeValueAsString(w.value()));
                    ps.setLong(6, now);
                    ps.setString(7, actor.length() > 64 ? actor.substring(0, 64) : actor);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not store the settings change in the database", e);
        }
    }

    /**
     * Writes every stored setting into the config files of the installed plugins.
     * Runs before those plugins are enabled.
     */
    public void applyToFiles() {
        record Row(String plugin, String file, String path, Object value) { }
        List<Row> rows = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT plugin, file, path, value FROM tea_settings ORDER BY updated_at, path");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3),
                        json.readValue(rs.getString(4), Object.class)));
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not read stored settings; the config files stay as they are", e);
            return;
        }
        Map<String, List<Row>> byFile = new LinkedHashMap<>();
        for (Row r : rows) {
            byFile.computeIfAbsent(r.plugin + "/" + r.file, k -> new ArrayList<>()).add(r);
        }
        for (List<Row> group : byFile.values()) {
            Row first = group.get(0);
            Plugin owner = Bukkit.getPluginManager().getPlugin(first.plugin);
            if (owner == null) {
                continue;
            }
            File target = new File(owner.getDataFolder(), first.file);
            try {
                if (!target.exists()) {
                    if (owner.getResource(first.file) == null) {
                        continue;
                    }
                    owner.saveResource(first.file, false);
                }
                String original = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
                String text = original;
                int applied = 0;
                for (Row r : group) {
                    Object current = SettingsValues.get(YamlPatcher.load(text), r.path);
                    if (!SettingsValues.same(current, r.value)) {
                        text = YamlPatcher.set(text, r.path, r.value);
                        applied++;
                    }
                }
                if (applied > 0) {
                    Files.write(target.toPath(), text.getBytes(StandardCharsets.UTF_8));
                    logger.info("Applied " + applied + " saved setting(s) to " + first.plugin + "/" + first.file + ".");
                }
            } catch (IOException | RuntimeException e) {
                logger.log(Level.WARNING, "Could not apply saved settings to " + target, e);
            }
        }
    }

    private static String escape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
