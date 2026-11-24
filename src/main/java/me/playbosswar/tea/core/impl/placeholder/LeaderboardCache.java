package me.playbosswar.tea.core.impl.placeholder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.stats.StatsRegistry;
import me.playbosswar.tea.core.api.stats.StatsSchema;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Board ids are {@code core_<stat>} and {@code <gameId>_<slug of column label>}.
 * Refreshed asynchronously so placeholders never query the database on the main thread.
 */
public final class LeaderboardCache {

    public static final int SIZE = 10;

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final Map<String, String> CORE_COLUMNS = Map.of(
            "wins", "wins", "losses", "losses", "games_played", "games_played",
            "kills", "kills", "deaths", "deaths", "assists", "assists");

    public record Entry(String name, String value) { }

    private final Plugin plugin;
    private final DataSource dataSource;
    private final StatsRegistry stats;
    private final Logger logger;
    private final Map<String, List<Entry>> boards = new ConcurrentHashMap<>();
    private BukkitTask task;

    public LeaderboardCache(Plugin plugin, DataSource dataSource, StatsRegistry stats, Logger logger) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.stats = stats;
        this.logger = logger;
    }

    public void start() {
        if (task != null) {
            return;
        }
        long period = Math.max(10L, plugin.getConfig().getLong("leaderboards.refresh-seconds", 60L)) * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 40L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** {@code position} is 1-based. */
    public Entry entry(String board, int position) {
        List<Entry> list = boards.get(board);
        if (list == null && StatAliases.board(board) != null) {
            list = boards.get(StatAliases.board(board));
        }
        if (list == null || position < 1 || position > list.size()) {
            return null;
        }
        return list.get(position - 1);
    }

    public java.util.Set<String> boardIds() {
        return boards.keySet();
    }

    public static String slug(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private void refresh() {
        Map<String, List<Entry>> fresh = new HashMap<>();
        for (Map.Entry<String, String> column : CORE_COLUMNS.entrySet()) {
            fresh.put("core_" + column.getKey(), top("tea_core_stats", column.getValue()));
        }
        for (StatsSchema schema : stats.allSchemas()) {
            for (StatsSchema.Column column : schema.columns()) {
                fresh.put(schema.gameId().value() + "_" + slug(column.label()),
                        top(schema.sqlTable(), column.sqlColumn()));
            }
        }
        boards.clear();
        boards.putAll(fresh);
    }

    private List<Entry> top(String table, String column) {
        List<Entry> out = new ArrayList<>();
        if (!SAFE_IDENTIFIER.matcher(table).matches() || !SAFE_IDENTIFIER.matcher(column).matches()) {
            return out;
        }
        String sql = "SELECT uuid, " + column + " FROM " + table + " ORDER BY " + column + " DESC LIMIT " + SIZE;
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(rs.getString(1)));
                String name = player.getName() == null ? rs.getString(1).substring(0, 8) : player.getName();
                out.add(new Entry(name, String.valueOf(rs.getLong(2))));
            }
        } catch (SQLException | IllegalArgumentException e) {
            logger.log(Level.FINE, "Leaderboard query failed for " + table + "." + column, e);
        }
        return out;
    }
}
