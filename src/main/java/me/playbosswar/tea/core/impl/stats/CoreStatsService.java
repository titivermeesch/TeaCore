package me.playbosswar.tea.core.impl.stats;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.combat.ArenaKillEvent;
import me.playbosswar.tea.core.api.combat.KillCredit;
import me.playbosswar.tea.core.api.team.TeamId;
import me.playbosswar.tea.core.api.team.TeamManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Tracks game-agnostic per-player stats - games played, wins, losses, kills,
 * deaths, assists - across every TeaCore game, so the core can expose totals
 * (and derived win-rate / KDR) as placeholders without each game implementing
 * its own counters. Wins/losses come from {@link MatchEndEvent} (every member of
 * a winning team wins; a draw counts as played only); kills/deaths/assists from {@link ArenaKillEvent}, which
 * the combat tracker fires for real deaths and for deaths games report
 * themselves. Writes run async off the event thread; reads are cached.
 */
public final class CoreStatsService implements Listener {

    /** Immutable snapshot of a player's core stats. */
    public record CoreStats(int gamesPlayed, int wins, int losses, int kills, int deaths, int assists) {
        static final CoreStats EMPTY = new CoreStats(0, 0, 0, 0, 0, 0);
    }

    private final Plugin plugin;
    private final DataSource dataSource;
    private final ArenaManager arenas;
    private final TeamManager teams;
    private final Logger logger;
    private final Cache<UUID, CoreStats> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(2_000)
            .build();

    public CoreStatsService(Plugin plugin, DataSource dataSource, ArenaManager arenas,
                            TeamManager teams, Logger logger) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.arenas = arenas;
        this.teams = teams;
        this.logger = logger;
    }

    public CoreStats statsOf(UUID uuid) {
        return cache.get(uuid, this::load);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchEnd(MatchEndEvent event) {
        Arena arena = event.arena();
        Set<UUID> participants = new HashSet<>(arena.players());
        UUID winner = event.winner().orElse(null);
        TeamId winningTeam = event.winningTeam().orElse(null);
        if (winner == null && winningTeam == null) {
            // A draw: everyone played, nobody won or lost.
            for (UUID uuid : participants) {
                bumpAsync(uuid, 1, 0, 0, 0, 0, 0);
            }
            return;
        }
        for (UUID uuid : participants) {
            boolean won = winningTeam != null
                    ? teams.teamOf(arena.id(), uuid).map(t -> t.id().equals(winningTeam)).orElse(false)
                    : uuid.equals(winner);
            bumpAsync(uuid, 1, won ? 1 : 0, won ? 0 : 1, 0, 0, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(ArenaKillEvent event) {
        KillCredit credit = event.credit();
        bumpAsync(credit.victim(), 0, 0, 0, 0, 1, 0);
        credit.killerId().ifPresent(killer -> bumpAsync(killer, 0, 0, 0, 1, 0, 0));
        for (UUID assister : credit.assists()) {
            bumpAsync(assister, 0, 0, 0, 0, 0, 1);
        }
    }

    private void bumpAsync(UUID uuid, int games, int wins, int losses, int kills, int deaths, int assists) {
        cache.invalidate(uuid);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> bump(uuid, games, wins, losses, kills, deaths, assists));
    }

    private void bump(UUID uuid, int games, int wins, int losses, int kills, int deaths, int assists) {
        // Portable increment-or-insert: try to UPDATE the existing row, and only
        // INSERT a fresh one if no row was touched. Avoids dialect-specific upsert.
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE tea_core_stats SET games_played = games_played + ?, wins = wins + ?, "
                            + "losses = losses + ?, kills = kills + ?, deaths = deaths + ?, "
                            + "assists = assists + ? WHERE uuid = ?")) {
                up.setInt(1, games);
                up.setInt(2, wins);
                up.setInt(3, losses);
                up.setInt(4, kills);
                up.setInt(5, deaths);
                up.setInt(6, assists);
                up.setString(7, uuid.toString());
                if (up.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO tea_core_stats (uuid, games_played, wins, losses, kills, deaths, assists) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setInt(2, games);
                ins.setInt(3, wins);
                ins.setInt(4, losses);
                ins.setInt(5, kills);
                ins.setInt(6, deaths);
                ins.setInt(7, assists);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to record core stats for " + uuid, e);
        } finally {
            cache.invalidate(uuid);
        }
    }

    private CoreStats load(UUID uuid) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT games_played, wins, losses, kills, deaths, assists FROM tea_core_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return CoreStats.EMPTY;
                }
                return new CoreStats(rs.getInt("games_played"), rs.getInt("wins"),
                        rs.getInt("losses"), rs.getInt("kills"), rs.getInt("deaths"),
                        rs.getInt("assists"));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load core stats for " + uuid, e);
            return CoreStats.EMPTY;
        }
    }
}
