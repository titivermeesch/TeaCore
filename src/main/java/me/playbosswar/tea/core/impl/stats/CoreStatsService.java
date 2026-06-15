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
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

/**
 * Tracks game-agnostic per-player stats - games played, wins, losses, kills,
 * deaths - across every TeaCore game, so the core can expose totals (and derived
 * win-rate / KDR) as placeholders without each game implementing its own
 * counters. Wins/losses come from {@link MatchEndEvent}; kills/deaths from
 * {@link PlayerDeathEvent} while the victim is in a live arena. Writes run async
 * off the event thread; reads are cached.
 */
public final class CoreStatsService implements Listener {

    /** Immutable snapshot of a player's core stats. */
    public record CoreStats(int gamesPlayed, int wins, int losses, int kills, int deaths) {
        static final CoreStats EMPTY = new CoreStats(0, 0, 0, 0, 0);
    }

    private final Plugin plugin;
    private final DataSource dataSource;
    private final ArenaManager arenas;
    private final Logger logger;
    private final Cache<UUID, CoreStats> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(2_000)
            .build();

    public CoreStatsService(Plugin plugin, DataSource dataSource, ArenaManager arenas, Logger logger) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.arenas = arenas;
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
        for (UUID uuid : participants) {
            boolean won = uuid.equals(winner);
            bumpAsync(uuid, 1, won ? 1 : 0, won ? 0 : 1, 0, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Arena arena = arenas.arenaOf(victim.getUniqueId()).orElse(null);
        if (arena == null || arena.state() != ArenaState.LIVE) {
            return;
        }
        bumpAsync(victim.getUniqueId(), 0, 0, 0, 0, 1);
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())
                && arena.players().contains(killer.getUniqueId())) {
            bumpAsync(killer.getUniqueId(), 0, 0, 0, 1, 0);
        }
    }

    private void bumpAsync(UUID uuid, int games, int wins, int losses, int kills, int deaths) {
        cache.invalidate(uuid);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> bump(uuid, games, wins, losses, kills, deaths));
    }

    private void bump(UUID uuid, int games, int wins, int losses, int kills, int deaths) {
        // Portable increment-or-insert: try to UPDATE the existing row, and only
        // INSERT a fresh one if no row was touched. Avoids dialect-specific upsert.
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE tea_core_stats SET games_played = games_played + ?, wins = wins + ?, "
                            + "losses = losses + ?, kills = kills + ?, deaths = deaths + ? WHERE uuid = ?")) {
                up.setInt(1, games);
                up.setInt(2, wins);
                up.setInt(3, losses);
                up.setInt(4, kills);
                up.setInt(5, deaths);
                up.setString(6, uuid.toString());
                if (up.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO tea_core_stats (uuid, games_played, wins, losses, kills, deaths) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setInt(2, games);
                ins.setInt(3, wins);
                ins.setInt(4, losses);
                ins.setInt(5, kills);
                ins.setInt(6, deaths);
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
                        "SELECT games_played, wins, losses, kills, deaths FROM tea_core_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return CoreStats.EMPTY;
                }
                return new CoreStats(rs.getInt("games_played"), rs.getInt("wins"),
                        rs.getInt("losses"), rs.getInt("kills"), rs.getInt("deaths"));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load core stats for " + uuid, e);
            return CoreStats.EMPTY;
        }
    }
}
