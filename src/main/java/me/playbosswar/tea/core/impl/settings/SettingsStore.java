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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.Change;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Settings staff changed, kept in the database so a server that starts later
 * gets them too, with a history of every change. TeaCore writes them into the
 * config files at startup, before any game plugin is enabled.
 *
 * <p>A value is stored for every server (scope {@code ""}) or for one server
 * group. A group value wins over the value for every server.
 */
public final class SettingsStore {

    /**
     * One change from the history.
     *
     * @param oldValue the value before; {@code null} when there was none
     * @param revertedBy the id of the history entry that undid this one, if any
     */
    public record Entry(String id, String fileId, String scope, String path, @Nullable Object oldValue,
                        @Nullable Object newValue, long at, String actor, @Nullable String revertedBy) { }

    private final DataSource db;
    private final Logger logger;
    private final ObjectMapper json = new ObjectMapper();

    public SettingsStore(DataSource db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Stores the writes of one change. A write replaces anything stored below its path.
     *
     * @param before the value of each written path before the change
     * @return the ids of the history entries, in the order of {@code writes}
     */
    public List<String> record(String fileId, String scope, String plugin, String file, List<Change> writes,
                               Map<String, Object> before, String actor, @Nullable String reverts) {
        List<String> ids = new ArrayList<>();
        String who = actor.length() > 64 ? actor.substring(0, 64) : actor;
        try (Connection c = db.getConnection()) {
            long now = System.currentTimeMillis();
            for (Change w : writes) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM tea_settings WHERE file_id = ? AND scope = ?"
                        + " AND (path = ? OR path LIKE ? ESCAPE '!' OR path LIKE ? ESCAPE '!')")) {
                    ps.setString(1, fileId);
                    ps.setString(2, scope);
                    ps.setString(3, w.path());
                    ps.setString(4, escape(w.path()) + ".%");
                    ps.setString(5, escape(w.path()) + "[%");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_settings "
                        + "(file_id, scope, path, plugin, file, value, updated_at, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, fileId);
                    ps.setString(2, scope);
                    ps.setString(3, w.path());
                    ps.setString(4, plugin);
                    ps.setString(5, file);
                    ps.setString(6, json.writeValueAsString(w.value()));
                    ps.setLong(7, now);
                    ps.setString(8, who);
                    ps.executeUpdate();
                }
                String id = UUID.randomUUID().toString();
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_settings_history "
                        + "(id, file_id, scope, path, old_value, new_value, at, actor) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, fileId);
                    ps.setString(3, scope);
                    ps.setString(4, w.path());
                    ps.setString(5, json.writeValueAsString(before.get(w.path())));
                    ps.setString(6, json.writeValueAsString(w.value()));
                    ps.setLong(7, now);
                    ps.setString(8, who);
                    ps.executeUpdate();
                }
                ids.add(id);
            }
            if (reverts != null && !ids.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE tea_settings_history SET reverted_by = ? WHERE id = ?")) {
                    ps.setString(1, ids.get(0));
                    ps.setString(2, reverts);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not store the settings change in the database", e);
        }
        return ids;
    }

    /** Newest first. */
    public List<Entry> history(@Nullable String fileId, int limit) {
        List<Entry> out = new ArrayList<>();
        String sql = "SELECT id, file_id, scope, path, old_value, new_value, at, actor, reverted_by"
                + " FROM tea_settings_history" + (fileId == null ? "" : " WHERE file_id = ?")
                + " ORDER BY at DESC, id LIMIT ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (fileId != null) {
                ps.setString(i++, fileId);
            }
            ps.setInt(i, Math.max(1, Math.min(500, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(entry(rs));
                }
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not read the settings history", e);
        }
        return out;
    }

    public Optional<Entry> entry(String id) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, file_id, scope, path, old_value, new_value, at, actor, reverted_by"
                        + " FROM tea_settings_history WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(entry(rs)) : Optional.empty();
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not read the settings history", e);
            return Optional.empty();
        }
    }

    private Entry entry(ResultSet rs) throws SQLException, IOException {
        return new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                read(rs.getString(5)), read(rs.getString(6)), rs.getLong(7), rs.getString(8), rs.getString(9));
    }

    private @Nullable Object read(@Nullable String s) throws IOException {
        return s == null ? null : json.readValue(s, Object.class);
    }

    /**
     * Writes every stored setting for every server, then those for {@code group},
     * into the config files of the installed plugins. Runs before those plugins
     * are enabled.
     */
    public void applyToFiles(String group) {
        record Row(String plugin, String file, String path, Object value) { }
        List<Row> rows = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT plugin, file, path, value FROM tea_settings"
                     + " WHERE scope = '' OR scope = ? ORDER BY CASE WHEN scope = '' THEN 0 ELSE 1 END, updated_at, path")) {
            ps.setString(1, group);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3), read(rs.getString(4))));
                }
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not read stored settings; the config files stay as they are", e);
            return;
        }
        Map<String, List<Row>> byFile = new LinkedHashMap<>();
        for (Row r : rows) {
            byFile.computeIfAbsent(r.plugin + "/" + r.file, k -> new ArrayList<>()).add(r);
        }
        for (List<Row> group0 : byFile.values()) {
            Row first = group0.get(0);
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
                for (Row r : group0) {
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
