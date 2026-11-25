package me.playbosswar.tea.core.impl.rating;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** An arena takes its mode (ranked or casual) from its first player. Ranked arenas only accept ratings within a window that widens while they wait. */
public final class RatingService implements Listener {

    public record Rating(int rating, int games, int wins, int losses) { }

    public record Row(int rank, UUID uuid, String name, int rating, int games, int wins, int losses) { }

    /** {@code since}: epoch ms when the first player joined. */
    private record Mode(boolean ranked, long since) { }

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final DataSource dataSource;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeaCore-ratings");
        t.setDaemon(true);
        return t;
    });
    private final Cache<String, Rating> ratings = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterAccess(Duration.ofHours(2)).build();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();
    private final Map<ArenaId, Mode> modes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> intents = new ConcurrentHashMap<>();
    /** Value: team id, or the player's own uuid without teams. */
    private final Map<ArenaId, Map<UUID, String>> lineups = new ConcurrentHashMap<>();
    private final Cache<String, List<Row>> boards = Caffeine.newBuilder()
            .maximumSize(200).expireAfterWrite(Duration.ofSeconds(30)).build();
    private final Cache<String, Integer> ranks = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterWrite(Duration.ofSeconds(60)).build();

    public RatingService(Plugin plugin, TeaCoreAPI core, DataSource dataSource, Logger logger) {
        this.plugin = plugin;
        this.core = core;
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean enabled(GameId game) {
        return core.features().enabled(game, "ranked");
    }

    private int start() {
        return plugin.getConfig().getInt("ranked.start-rating", 1000);
    }

    private int kFactor() {
        return Math.max(1, plugin.getConfig().getInt("ranked.k-factor", 32));
    }

    public int range(long waitedMs) {
        int base = plugin.getConfig().getInt("ranked.range", 150);
        int widen = plugin.getConfig().getInt("ranked.widen-per-10s", 50);
        int max = plugin.getConfig().getInt("ranked.max-range", 600);
        return (int) Math.min(max, base + widen * (waitedMs / 10_000L));
    }

    private static String key(UUID uuid, GameId game) {
        return uuid + "|" + game.value();
    }

    /** Returns the start rating until loaded and starts a background load. Display and matchmaking only. */
    public Rating get(UUID uuid, GameId game) {
        Rating r = ratings.getIfPresent(key(uuid, game));
        if (r != null) {
            return r;
        }
        load(uuid, game);
        return new Rating(start(), 0, 0, 0);
    }

    public void load(UUID uuid, GameId game) {
        if (ratings.getIfPresent(key(uuid, game)) == null) {
            reload(uuid, game);
        }
    }

    private void reload(UUID uuid, GameId game) {
        String k = key(uuid, game);
        if (loading.putIfAbsent(k, Boolean.TRUE) != null) {
            return;
        }
        writer.execute(() -> {
            try {
                ratings.put(k, read(uuid, game));
            } finally {
                loading.remove(k);
            }
        });
    }

    /** Blocking. */
    public Rating read(UUID uuid, GameId game) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT rating, games, wins, losses FROM tea_ratings WHERE player_uuid = ? AND game_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, game.value());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Rating(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not read rating of " + uuid, e);
        }
        return new Rating(start(), 0, 0, 0);
    }

    /**
     * Adds a match result to the stored row instead of writing absolute values,
     * so a result from another server since this one last read the row is kept.
     */
    private void applyResult(Connection c, UUID uuid, String name, GameId game, int delta, boolean won, boolean lost)
            throws SQLException {
        long now = System.currentTimeMillis();
        String update = "UPDATE tea_ratings SET player_name = ?, "
                + "rating = CASE WHEN rating + ? < 0 THEN 0 ELSE rating + ? END, games = games + 1, "
                + "wins = wins + ?, losses = losses + ?, updated_at = ? WHERE player_uuid = ? AND game_id = ?";
        for (int attempt = 0; attempt < 2; attempt++) {
            try (PreparedStatement up = c.prepareStatement(update)) {
                up.setString(1, name);
                up.setInt(2, delta);
                up.setInt(3, delta);
                up.setInt(4, won ? 1 : 0);
                up.setInt(5, lost ? 1 : 0);
                up.setLong(6, now);
                up.setString(7, uuid.toString());
                up.setString(8, game.value());
                if (up.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO tea_ratings (player_uuid, game_id, "
                    + "player_name, rating, games, wins, losses, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setString(2, game.value());
                ins.setString(3, name);
                ins.setInt(4, Math.max(0, start() + delta));
                ins.setInt(5, 1);
                ins.setInt(6, won ? 1 : 0);
                ins.setInt(7, lost ? 1 : 0);
                ins.setLong(8, now);
                ins.executeUpdate();
                return;
            } catch (SQLException duplicate) {
                // Another server inserted the row first; the second pass updates it.
                if (attempt == 1) {
                    throw duplicate;
                }
            }
        }
    }

    /** Blocking; cached for 30 s. */
    public List<Row> leaderboard(GameId game, int limit) {
        return boards.get(game.value() + "|" + limit, k -> {
            List<Row> out = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT player_uuid, player_name, rating, games, wins, "
                         + "losses FROM tea_ratings WHERE game_id = ? AND games > 0 ORDER BY rating DESC, "
                         + "updated_at ASC LIMIT ?")) {
                ps.setString(1, game.value());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 0;
                    while (rs.next()) {
                        out.add(new Row(++rank, UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3),
                                rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Could not read the " + game + " leaderboard", e);
            }
            return out;
        });
    }

    /** 1 = best, 0 without ranked games. Blocking; cached 60 s. */
    public int rank(UUID uuid, GameId game) {
        return ranks.get(key(uuid, game), k -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM tea_ratings o, tea_ratings me "
                         + "WHERE me.player_uuid = ? AND me.game_id = ? AND me.games > 0 AND o.game_id = me.game_id "
                         + "AND o.games > 0 AND o.rating > me.rating")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, game.value());
                try (ResultSet rs = ps.executeQuery()) {
                    Rating mine = read(uuid, game);
                    return rs.next() && mine.games() > 0 ? rs.getInt(1) + 1 : 0;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Could not read the rank of " + uuid, e);
                return 0;
            }
        });
    }

    /** Non-blocking: the cached place, or 0 while it loads in the background. */
    public int rankCached(UUID uuid, GameId game) {
        Integer cached = ranks.getIfPresent(key(uuid, game));
        if (cached != null) {
            return cached;
        }
        String k = "rank|" + key(uuid, game);
        if (loading.putIfAbsent(k, Boolean.TRUE) == null) {
            writer.execute(() -> {
                try {
                    rank(uuid, game);
                } finally {
                    loading.remove(k);
                }
            });
        }
        return 0;
    }

    public void intend(UUID uuid, boolean ranked) {
        intents.put(uuid, ranked);
    }

    /** Empty until a player has decided the mode. */
    public Optional<Boolean> ranked(ArenaId arena) {
        Mode m = modes.get(arena);
        return m == null ? Optional.empty() : Optional.of(m.ranked());
    }

    public int average(Arena arena) {
        if (arena.players().isEmpty()) {
            return start();
        }
        long sum = 0;
        for (UUID uuid : arena.players()) {
            sum += get(uuid, arena.gameId()).rating();
        }
        return (int) (sum / arena.players().size());
    }

    public int range(Arena arena) {
        Mode m = modes.get(arena.id());
        return m == null ? range(0) : range(System.currentTimeMillis() - m.since());
    }

    public boolean accepts(Arena arena, boolean ranked, int rating) {
        Mode m = modes.get(arena.id());
        if (m == null || arena.players().isEmpty()) {
            return true;
        }
        if (m.ranked() != ranked) {
            return false;
        }
        return !ranked || Math.abs(average(arena) - rating) <= range(arena);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(ArenaPlayerJoinEvent event) {
        UUID uuid = event.player().getUniqueId();
        Boolean intent = intents.remove(uuid);
        if (event.spectator()) {
            return;
        }
        Arena arena = event.arena();
        load(uuid, arena.gameId());
        Mode m = modes.get(arena.id());
        if (m == null || arena.players().size() <= 1) {
            boolean ranked = Boolean.TRUE.equals(intent) && enabled(arena.gameId());
            modes.put(arena.id(), new Mode(ranked, System.currentTimeMillis()));
            m = modes.get(arena.id());
        }
        if (m.ranked()) {
            Rating r = get(uuid, arena.gameId());
            core.messageService().send(event.player(), "core.ranked.joined", Map.of(
                    "rating", String.valueOf(r.rating())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        Arena arena = event.arena();
        if (arena.players().isEmpty() && arena.state() != ArenaState.LIVE && arena.state() != ArenaState.ENDING) {
            modes.remove(arena.id());
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        java.util.Set<GameId> games = new java.util.HashSet<>(core.coordination().allHostedGames());
        core.games().all().forEach(d -> games.add(d.id()));
        for (GameId game : games) {
            reload(uuid, game);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        intents.remove(uuid);
        String prefix = uuid + "|";
        ratings.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        ranks.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onState(ArenaStateChangeEvent event) {
        Arena arena = event.arena();
        if (event.to() == ArenaState.LIVE && ranked(arena.id()).orElse(false)) {
            Map<UUID, String> lineup = new HashMap<>();
            for (UUID uuid : arena.players()) {
                lineup.put(uuid, core.teams().teamOf(arena.id(), uuid).map(t -> t.id().value())
                        .orElse(uuid.toString()));
            }
            lineups.put(arena.id(), lineup);
        } else if (event.to() == ArenaState.RELEASED || event.to() == ArenaState.RESETTING) {
            modes.remove(arena.id());
            lineups.remove(arena.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        Arena arena = event.arena();
        Map<UUID, String> lineup = lineups.remove(arena.id());
        if (lineup == null || lineup.size() < 2) {
            return;
        }
        GameId game = arena.gameId();
        String winningSide = event.winningTeam().map(t -> t.value())
                .orElse(event.winner().map(UUID::toString).orElse(null));
        // Side ratings are member averages.
        Map<String, List<UUID>> sides = new HashMap<>();
        lineup.forEach((uuid, side) -> sides.computeIfAbsent(side, k -> new ArrayList<>()).add(uuid));
        if (sides.size() < 2) {
            return;
        }
        java.util.Set<UUID> present = new java.util.HashSet<>(arena.players());
        Map<UUID, String> names = new HashMap<>();
        for (UUID uuid : lineup.keySet()) {
            names.put(uuid, Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName())
                    .orElse(uuid.toString().substring(0, 8)));
        }
        int k = kFactor();
        writer.execute(() -> settle(game, lineup, sides, winningSide, present, names, k));
    }

    /** Runs on the writer thread: reads the stored ratings, then adds the result to each row. */
    private void settle(GameId game, Map<UUID, String> lineup, Map<String, List<UUID>> sides, String winningSide,
                        java.util.Set<UUID> present, Map<UUID, String> names, int k) {
        Map<UUID, Integer> before = new HashMap<>();
        for (UUID uuid : lineup.keySet()) {
            before.put(uuid, read(uuid, game).rating());
        }
        Map<UUID, Integer> deltas = new HashMap<>();
        for (Map.Entry<String, List<UUID>> side : sides.entrySet()) {
            double mine = side.getValue().stream().mapToInt(before::get).average().orElse(start());
            double others = lineup.keySet().stream().filter(u -> !side.getValue().contains(u))
                    .mapToInt(before::get).average().orElse(start());
            double expected = 1.0 / (1.0 + Math.pow(10, (others - mine) / 400.0));
            double score = winningSide == null ? 0.5 : side.getKey().equals(winningSide) ? 1.0 : 0.0;
            for (UUID uuid : side.getValue()) {
                // Leaving before the end counts as a loss.
                double s = present.contains(uuid) ? score : 0.0;
                deltas.put(uuid, (int) Math.round(k * (s - expected)));
            }
        }
        Map<UUID, Rating> after = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : deltas.entrySet()) {
            UUID uuid = e.getKey();
            boolean won = e.getValue() > 0 && winningSide != null;
            boolean lost = winningSide != null && !won;
            try (Connection c = dataSource.getConnection()) {
                applyResult(c, uuid, names.get(uuid), game, e.getValue(), won, lost);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Could not save rating of " + uuid, ex);
                continue;
            }
            Rating updated = read(uuid, game);
            ratings.put(key(uuid, game), updated);
            after.put(uuid, updated);
        }
        boards.invalidateAll();
        ranks.invalidateAll();
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> after.forEach((uuid, updated) -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                int d = deltas.get(uuid);
                core.messageService().send(p, d >= 0 ? "core.ranked.gained" : "core.ranked.lost", Map.of(
                        "change", String.valueOf(Math.abs(d)), "rating", String.valueOf(updated.rating())));
            }
        }));
    }

}
