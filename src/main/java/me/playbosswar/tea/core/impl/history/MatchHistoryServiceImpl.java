package me.playbosswar.tea.core.impl.history;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.combat.MatchStats;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.history.MatchHistoryService;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.api.team.TeamId;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class MatchHistoryServiceImpl implements MatchHistoryService, Listener {

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final DataSource dataSource;
    private final Logger logger;
    private final Map<ArenaId, Instant> liveSince = new ConcurrentHashMap<>();

    public MatchHistoryServiceImpl(Plugin plugin, TeaCoreAPI core, DataSource dataSource, Logger logger) {
        this.plugin = plugin;
        this.core = core;
        this.dataSource = dataSource;
        this.logger = logger;
    }

    /** Early leavers per arena, recorded with result "left". */
    private final Map<ArenaId, Map<UUID, Object[]>> leavers = new ConcurrentHashMap<>();

    @EventHandler
    public void onState(ArenaStateChangeEvent event) {
        if (event.to() == ArenaState.LIVE) {
            liveSince.put(event.arena().id(), Instant.now());
            leavers.remove(event.arena().id());
        } else if (event.to() == ArenaState.RELEASED) {
            liveSince.remove(event.arena().id());
            leavers.remove(event.arena().id());
        }
    }

    /** Runs first so the leaver's team is still known. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeave(me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent event) {
        Arena arena = event.arena();
        if (arena.state() != ArenaState.LIVE
                || event.reason() == me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent.LeaveReason.MATCH_ENDED
                || arena.spectators().contains(event.player().getUniqueId())) {
            return;
        }
        UUID uuid = event.player().getUniqueId();
        String team = core.teams().teamOf(arena.id(), uuid).map(Team::id).map(TeamId::value).orElse(null);
        MatchStats stats = core.combat().matchStats(arena.id(), uuid);
        leavers.computeIfAbsent(arena.id(), k -> new ConcurrentHashMap<>()).put(uuid, new Object[]{
                uuid.toString(), team, stats.kills(), stats.deaths(), stats.assists(), "left",
                event.player().getName()});
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        if (!core.features().enabled("match-history")) {
            return;
        }
        Arena arena = event.arena();
        Instant end = Instant.now();
        Instant start = liveSince.remove(arena.id());
        TeamId winningTeam = event.winningTeam().orElse(null);
        UUID winner = event.winner().orElse(null);
        boolean draw = winningTeam == null && winner == null;
        // Snapshot everything on the main thread, write it asynchronously.
        List<Object[]> rows = new ArrayList<>();
        for (UUID uuid : arena.players()) {
            String team = core.teams().teamOf(arena.id(), uuid).map(Team::id).map(TeamId::value).orElse(null);
            MatchStats stats = core.combat().matchStats(arena.id(), uuid);
            String result = draw ? "draw"
                    : winningTeam != null ? (winningTeam.value().equals(team) ? "win" : "loss")
                    : (uuid.equals(winner) ? "win" : "loss");
            org.bukkit.entity.Player online = plugin.getServer().getPlayer(uuid);
            String name = online != null ? online.getName() : plugin.getServer().getOfflinePlayer(uuid).getName();
            rows.add(new Object[]{uuid.toString(), team, stats.kills(), stats.deaths(), stats.assists(), result, name});
        }
        Map<UUID, Object[]> left = leavers.remove(arena.id());
        if (left != null) {
            for (Map.Entry<UUID, Object[]> e : left.entrySet()) {
                // Rejoined before the end: their final line above already counts.
                if (!arena.players().contains(e.getKey())) {
                    rows.add(e.getValue());
                }
            }
        }
        MatchRecord record = new MatchRecord(arena.matchId(), arena.gameId().value(), arena.mapId().orElse(null),
                core.serverId(), end, start == null ? 0 : (int) Math.max(0, end.getEpochSecond() - start.getEpochSecond()),
                winningTeam == null ? null : winningTeam.value(), winner);
        Instant started = start;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(record, started, rows));
    }

    private void write(MatchRecord record, @Nullable Instant started, List<Object[]> rows) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_match_history (match_id, game_id, "
                    + "map_id, server_id, started_at, ended_at, duration_seconds, winner_team, winner_uuid) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, record.matchId().toString());
                ps.setString(2, record.gameId());
                ps.setString(3, record.mapId());
                ps.setString(4, record.serverId());
                ps.setTimestamp(5, started == null ? null : Timestamp.from(started));
                ps.setTimestamp(6, Timestamp.from(record.endedAt()));
                ps.setInt(7, record.durationSeconds());
                ps.setString(8, record.winnerTeam());
                ps.setString(9, record.winnerUuid() == null ? null : record.winnerUuid().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_match_players (match_id, uuid, team, "
                    + "kills, deaths, assists, result, name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Object[] row : rows) {
                    ps.setString(1, record.matchId().toString());
                    ps.setString(2, (String) row[0]);
                    ps.setString(3, (String) row[1]);
                    ps.setInt(4, (Integer) row[2]);
                    ps.setInt(5, (Integer) row[3]);
                    ps.setInt(6, (Integer) row[4]);
                    ps.setString(7, (String) row[5]);
                    ps.setString(8, (String) row[6]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not record match " + record.matchId(), e);
        }
    }

    private static final String MATCH_COLUMNS = "h.match_id, h.game_id, h.map_id, h.server_id, h.ended_at, "
            + "h.duration_seconds, h.winner_team, h.winner_uuid";

    @Override
    public List<PlayerMatch> recentFor(UUID player, int limit) {
        String sql = "SELECT " + MATCH_COLUMNS + ", p.team, p.kills, p.deaths, p.assists, p.result, p.uuid, p.name "
                + "FROM tea_match_players p JOIN tea_match_history h ON h.match_id = p.match_id "
                + "WHERE p.uuid = ? ORDER BY h.ended_at DESC LIMIT ?";
        List<PlayerMatch> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(playerLine(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not read match history for " + player, e);
        }
        return out;
    }

    @Override
    public List<MatchRecord> recent(@Nullable GameId game, int limit) {
        String sql = "SELECT " + MATCH_COLUMNS + " FROM tea_match_history h"
                + (game == null ? "" : " WHERE h.game_id = ?") + " ORDER BY h.ended_at DESC LIMIT ?";
        List<MatchRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (game != null) {
                ps.setString(i++, game.value());
            }
            ps.setInt(i, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(match(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not read match history", e);
        }
        return out;
    }

    @Override
    public List<PlayerMatch> participants(UUID matchId) {
        String sql = "SELECT " + MATCH_COLUMNS + ", p.team, p.kills, p.deaths, p.assists, p.result, p.uuid, p.name "
                + "FROM tea_match_players p JOIN tea_match_history h ON h.match_id = p.match_id "
                + "WHERE p.match_id = ? ORDER BY p.kills DESC";
        List<PlayerMatch> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, matchId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(playerLine(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not read match " + matchId, e);
        }
        return out;
    }

    private record Where(String sql, List<Object> params) { }

    private static Where where(Query q) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (q.game() != null && !q.game().isBlank()) {
            sql.append(" AND h.game_id = ?");
            params.add(q.game());
        }
        if (q.map() != null && !q.map().isBlank()) {
            sql.append(" AND h.map_id = ?");
            params.add(q.map());
        }
        if (q.player() != null && !q.player().isBlank()) {
            String p = q.player().trim();
            sql.append(" AND EXISTS (SELECT 1 FROM tea_match_players x WHERE x.match_id = h.match_id"
                    + " AND (x.uuid = ? OR LOWER(x.name) = ?))");
            params.add(p.toLowerCase(java.util.Locale.ROOT));
            params.add(p.toLowerCase(java.util.Locale.ROOT));
        }
        if (q.from() != null) {
            sql.append(" AND h.ended_at >= ?");
            params.add(Timestamp.from(q.from()));
        }
        if (q.to() != null) {
            sql.append(" AND h.ended_at < ?");
            params.add(Timestamp.from(q.to()));
        }
        return new Where(sql.toString(), params);
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            if (v instanceof Timestamp t) {
                ps.setTimestamp(i + 1, t);
            } else {
                ps.setString(i + 1, String.valueOf(v));
            }
        }
    }

    @Override
    public List<MatchRecord> search(Query q) {
        Where w = where(q);
        String sql = "SELECT " + MATCH_COLUMNS + " FROM tea_match_history h" + w.sql()
                + " ORDER BY h.ended_at DESC LIMIT ? OFFSET ?";
        List<MatchRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, w.params());
            ps.setInt(w.params().size() + 1, Math.max(1, Math.min(500, q.limit())));
            ps.setInt(w.params().size() + 2, Math.max(0, q.offset()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(match(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not search match history", e);
        }
        return out;
    }

    @Override
    public int count(Query q) {
        Where w = where(q);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM tea_match_history h" + w.sql())) {
            bind(ps, w.params());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not count match history", e);
            return 0;
        }
    }

    @Override
    public int pruneBefore(Instant cutoff) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM tea_match_players WHERE match_id IN "
                    + "(SELECT match_id FROM tea_match_history WHERE ended_at < ?)")) {
                ps.setTimestamp(1, Timestamp.from(cutoff));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM tea_match_history WHERE ended_at < ?")) {
                ps.setTimestamp(1, Timestamp.from(cutoff));
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not prune match history", e);
            return 0;
        }
    }

    private org.bukkit.scheduler.BukkitTask pruneTask;

    /** {@code keepDays <= 0} keeps everything. */
    public void startRetention(int keepDays) {
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
        if (keepDays <= 0) {
            return;
        }
        pruneTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int removed = pruneBefore(Instant.now().minus(java.time.Duration.ofDays(keepDays)));
            if (removed > 0) {
                logger.info("Match history: removed " + removed + " match(es) older than " + keepDays + " days.");
            }
        }, 20L * 60, 20L * 60 * 60 * 24);
    }

    private static MatchRecord match(ResultSet rs) throws SQLException {
        String winner = rs.getString("winner_uuid");
        Timestamp ended = rs.getTimestamp("ended_at");
        return new MatchRecord(UUID.fromString(rs.getString("match_id")), rs.getString("game_id"),
                rs.getString("map_id"), rs.getString("server_id"),
                ended == null ? Instant.EPOCH : ended.toInstant(), rs.getInt("duration_seconds"),
                rs.getString("winner_team"), winner == null ? null : UUID.fromString(winner));
    }

    private static PlayerMatch playerLine(ResultSet rs) throws SQLException {
        String uuid = rs.getString("uuid");
        return new PlayerMatch(match(rs), rs.getString("team"), rs.getInt("kills"), rs.getInt("deaths"),
                rs.getInt("assists"), rs.getString("result"), uuid == null ? null : UUID.fromString(uuid),
                rs.getString("name"));
    }
}
