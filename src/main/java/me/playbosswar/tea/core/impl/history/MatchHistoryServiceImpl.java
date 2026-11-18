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

/** Records finished matches into tea_match_history / tea_match_players. */
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

    @EventHandler
    public void onState(ArenaStateChangeEvent event) {
        if (event.to() == ArenaState.LIVE) {
            liveSince.put(event.arena().id(), Instant.now());
        } else if (event.to() == ArenaState.RELEASED) {
            liveSince.remove(event.arena().id());
        }
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
            rows.add(new Object[]{uuid.toString(), team, stats.kills(), stats.deaths(), stats.assists(), result});
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
                    + "kills, deaths, assists, result) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (Object[] row : rows) {
                    ps.setString(1, record.matchId().toString());
                    ps.setString(2, (String) row[0]);
                    ps.setString(3, (String) row[1]);
                    ps.setInt(4, (Integer) row[2]);
                    ps.setInt(5, (Integer) row[3]);
                    ps.setInt(6, (Integer) row[4]);
                    ps.setString(7, (String) row[5]);
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
        String sql = "SELECT " + MATCH_COLUMNS + ", p.team, p.kills, p.deaths, p.assists, p.result "
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
        String sql = "SELECT " + MATCH_COLUMNS + ", p.team, p.kills, p.deaths, p.assists, p.result "
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

    private static MatchRecord match(ResultSet rs) throws SQLException {
        String winner = rs.getString("winner_uuid");
        Timestamp ended = rs.getTimestamp("ended_at");
        return new MatchRecord(UUID.fromString(rs.getString("match_id")), rs.getString("game_id"),
                rs.getString("map_id"), rs.getString("server_id"),
                ended == null ? Instant.EPOCH : ended.toInstant(), rs.getInt("duration_seconds"),
                rs.getString("winner_team"), winner == null ? null : UUID.fromString(winner));
    }

    private static PlayerMatch playerLine(ResultSet rs) throws SQLException {
        return new PlayerMatch(match(rs), rs.getString("team"), rs.getInt("kills"), rs.getInt("deaths"),
                rs.getInt("assists"), rs.getString("result"));
    }
}
