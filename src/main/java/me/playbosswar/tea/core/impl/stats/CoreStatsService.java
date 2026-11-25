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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Writes and loads run in order on one thread, which {@link #close()} drains.
 * Reads from the main thread use {@link #cachedStatsOf(UUID)}, which never blocks.
 */
public final class CoreStatsService implements Listener {

    public record CoreStats(int gamesPlayed, int wins, int losses, int kills, int deaths, int assists) {
        static final CoreStats EMPTY = new CoreStats(0, 0, 0, 0, 0, 0);

        CoreStats plus(int g, int w, int l, int k, int d, int a) {
            return new CoreStats(gamesPlayed + g, wins + w, losses + l, kills + k, deaths + d, assists + a);
        }
    }

    private record Entry(CoreStats stats, long loadedAt) { }

    private static final long REFRESH_MS = Duration.ofMinutes(2).toMillis();

    private final Plugin plugin;
    private final DataSource dataSource;
    private final ArenaManager arenas;
    private final TeamManager teams;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeaCore-stats");
        t.setDaemon(true);
        return t;
    });
    private final Cache<UUID, Entry> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    public CoreStatsService(Plugin plugin, DataSource dataSource, ArenaManager arenas,
                            TeamManager teams, Logger logger) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.arenas = arenas;
        this.teams = teams;
        this.logger = logger;
    }

    /** Blocking on a cache miss; for commands that look up offline players. */
    public CoreStats statsOf(UUID uuid) {
        Entry e = cache.getIfPresent(uuid);
        if (e != null) {
            return e.stats();
        }
        try {
            // Through the writer thread, so the result includes writes still queued.
            return io.submit(() -> {
                CoreStats loaded = load(uuid);
                cache.put(uuid, new Entry(loaded, System.currentTimeMillis()));
                return loaded;
            }).get();
        } catch (RejectedExecutionException | java.util.concurrent.ExecutionException failed) {
            return load(uuid);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CoreStats.EMPTY;
        }
    }

    /** The last known stats, or zeros while the first load runs in the background. */
    public CoreStats cachedStatsOf(UUID uuid) {
        Entry e = cache.getIfPresent(uuid);
        if (e == null || System.currentTimeMillis() - e.loadedAt() > REFRESH_MS) {
            loadAsync(uuid);
        }
        return e == null ? CoreStats.EMPTY : e.stats();
    }

    private void loadAsync(UUID uuid) {
        if (!loading.add(uuid)) {
            return;
        }
        try {
            io.execute(() -> {
                try {
                    cache.put(uuid, new Entry(load(uuid), System.currentTimeMillis()));
                } finally {
                    loading.remove(uuid);
                }
            });
        } catch (RejectedExecutionException closed) {
            loading.remove(uuid);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadAsync(event.getPlayer().getUniqueId());
    }

    /** Finishes queued writes. Call before the data source closes. */
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Some stat writes did not finish before shutdown.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        try {
            io.execute(() -> {
                if (bump(uuid, games, wins, losses, kills, deaths, assists)) {
                    // Loads run on this thread too, so an entry here already reflects earlier writes.
                    cache.asMap().computeIfPresent(uuid,
                            (k, e) -> new Entry(e.stats().plus(games, wins, losses, kills, deaths, assists), e.loadedAt()));
                }
            });
        } catch (RejectedExecutionException closed) {
            logger.warning("Dropped a stat update for " + uuid + " during shutdown.");
        }
    }

    private boolean bump(UUID uuid, int games, int wins, int losses, int kills, int deaths, int assists) {
        try (Connection c = dataSource.getConnection()) {
            for (int attempt = 0; attempt < 2; attempt++) {
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
                        return true;
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
                    return true;
                } catch (SQLException duplicate) {
                    // Another server inserted the row between our UPDATE and INSERT; the next pass updates it.
                    if (attempt == 1) {
                        throw duplicate;
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to record core stats for " + uuid, e);
        }
        return false;
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
