package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The server running the dashboard records the network's players, queue and
 * arenas once a minute; the charts read those samples and the match history.
 */
final class DashboardChartsApi {

    private static final long KEEP_MS = 35L * 24 * 3600 * 1000;

    private final Plugin plugin;
    private final DataSource db;
    private final CoordinationBackend coordination;
    private final DashboardAuth auth;
    private final IntSupplier networkPlayers;
    private final Logger logger;
    private BukkitTask task;

    DashboardChartsApi(Plugin plugin, DataSource db, CoordinationBackend coordination, DashboardAuth auth,
                       IntSupplier networkPlayers, Logger logger) {
        this.plugin = plugin;
        this.db = db;
        this.coordination = coordination;
        this.auth = auth;
        this.networkPlayers = networkPlayers;
        this.logger = logger;
    }

    void register(Javalin app) {
        app.get("/api/charts", auth.guard(DashboardPermission.VIEW, this::charts));
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sample, 20L * 30, 20L * 60);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void sample() {
        int players = networkPlayers.getAsInt();
        if (players < 0) {
            players = Bukkit.getOnlinePlayers().size();
        }
        int queued = 0;
        for (GameId game : coordination.allHostedGames()) {
            queued += coordination.queueFor(game).size();
        }
        int arenas = 0;
        int live = 0;
        for (ArenaSnapshot s : coordination.allArenas()) {
            if (s.state() != ArenaState.RELEASED) {
                arenas++;
            }
            if (s.state() == ArenaState.LIVE) {
                live++;
            }
        }
        long now = System.currentTimeMillis() / 60_000 * 60_000;
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM tea_dashboard_samples WHERE at = ? OR at < ?")) {
                ps.setLong(1, now);
                ps.setLong(2, now - KEEP_MS);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO tea_dashboard_samples (at, players, queued, arenas, live) VALUES (?, ?, ?, ?, ?)")) {
                ps.setLong(1, now);
                ps.setInt(2, players);
                ps.setInt(3, queued);
                ps.setInt(4, arenas);
                ps.setInt(5, live);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.FINE, "Could not record a dashboard sample", e);
        }
    }

    /** {@code range}: 24h (5-minute points), 7d (hourly) or 30d (every 6 hours). */
    private void charts(Context ctx) {
        String range = ctx.queryParamAsClass("range", String.class).getOrDefault("24h");
        long span = switch (range) {
            case "7d" -> 7L * 24 * 3600 * 1000;
            case "30d" -> 30L * 24 * 3600 * 1000;
            default -> 24L * 3600 * 1000;
        };
        long bucket = switch (range) {
            case "7d" -> 3600L * 1000;
            case "30d" -> 6L * 3600 * 1000;
            default -> 5L * 60 * 1000;
        };
        long now = System.currentTimeMillis();
        long from = now - span;
        Map<Long, long[]> buckets = new TreeMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT at, players, queued, arenas, live FROM tea_dashboard_samples WHERE at >= ? ORDER BY at")) {
            ps.setLong(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long key = rs.getLong(1) / bucket * bucket;
                    long[] acc = buckets.computeIfAbsent(key, k -> new long[6]);
                    acc[0] += rs.getInt(2);
                    acc[1] += rs.getInt(3);
                    acc[2] += rs.getInt(4);
                    acc[3] += rs.getInt(5);
                    acc[4]++;
                    acc[5] = Math.max(acc[5], rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            ctx.status(500).json(Map.of("error", "could not read samples"));
            return;
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : buckets.entrySet()) {
            long[] a = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", e.getKey());
            m.put("players", round(a[0], a[4]));
            m.put("peakPlayers", a[5]);
            m.put("queued", round(a[1], a[4]));
            m.put("arenas", round(a[2], a[4]));
            m.put("live", round(a[3], a[4]));
            samples.add(m);
        }

        // Matches per day and game, counted in Java so the query works on SQLite and MySQL alike.
        ZoneId zone = ZoneId.systemDefault();
        long matchesFrom = now - Math.max(span, 7L * 24 * 3600 * 1000);
        Map<String, Map<String, Integer>> perDay = new TreeMap<>();
        LocalDate first = Instant.ofEpochMilli(matchesFrom).atZone(zone).toLocalDate();
        for (LocalDate d = first; !d.isAfter(LocalDate.now(zone)); d = d.plusDays(1)) {
            perDay.put(d.toString(), new TreeMap<>());
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT ended_at, game_id FROM tea_match_history WHERE ended_at >= ?")) {
            ps.setTimestamp(1, new Timestamp(matchesFrom));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp t = rs.getTimestamp(1);
                    if (t == null) {
                        continue;
                    }
                    String day = t.toInstant().atZone(zone).toLocalDate().toString();
                    perDay.computeIfAbsent(day, k -> new TreeMap<>()).merge(rs.getString(2), 1, Integer::sum);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.FINE, "Could not count matches for the charts", e);
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : perDay.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", e.getKey());
            m.put("games", e.getValue());
            matches.add(m);
        }
        ctx.json(Map.of("range", range, "bucketMs", bucket, "samples", samples, "matches", matches));
    }

    private static double round(long sum, long count) {
        return count == 0 ? 0 : Math.round(sum * 10.0 / count) / 10.0;
    }
}
