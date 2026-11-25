package me.playbosswar.tea.core.impl.achievement;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.achievement.AchievementDefinition;
import me.playbosswar.tea.core.api.achievement.AchievementService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Unlock state lives in {@code tea_achievements}; its primary key makes a repeat unlock a no-op.
 * Menus and placeholders read a per-player cache that loads in the background.
 */
public final class AchievementServiceImpl implements AchievementService, Listener {

    private record Unlocked(Set<String> keys, long loadedAt) { }

    private static final long REFRESH_MS = 120_000;

    private final DataSource dataSource;
    private final Logger logger;
    private final MessageService messages;
    /** Key: "<gameId>:<id>". */
    private final Map<String, AchievementDefinition> definitions = new ConcurrentHashMap<>();

    public interface UnlockListener {
        void accept(String player, GameId game, String achievementName);
    }

    private volatile UnlockListener unlockListener;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeaCore-achievements");
        t.setDaemon(true);
        return t;
    });
    private final Cache<UUID, Unlocked> unlocked = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(30)).build();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean storedLoading = new AtomicBoolean();

    public void setUnlockListener(UnlockListener listener) {
        this.unlockListener = listener;
    }

    public AchievementServiceImpl(DataSource dataSource, Logger logger, MessageService messages) {
        this.dataSource = dataSource;
        this.logger = logger;
        this.messages = messages;
        storedCached();
    }

    @Override
    public void register(AchievementDefinition definition) {
        int position = (int) definitions.values().stream().filter(d -> d.gameId().equals(definition.gameId())).count();
        definitions.put(key(definition.gameId(), definition.id()), definition);
        store(definition, position);
    }

    /** A definition from {@code tea_achievement_defs}, for servers without the game's plugin. Texts are legacy-coloured. */
    public record Stored(GameId gameId, String id, org.bukkit.Material icon, String name, String description) { }

    /** Rendered in the default locale so other servers can list it. */
    private void store(AchievementDefinition def, int position) {
        String locale = messages.defaultLocale();
        String name = messages.renderLegacyNoPrefix(locale, def.nameKey(), Map.of());
        String desc = messages.renderLegacyNoPrefix(locale, def.descKey(), Map.of());
        try (Connection c = dataSource.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM tea_achievement_defs WHERE game_id = ? AND achievement = ?")) {
                    del.setString(1, def.gameId().value());
                    del.setString(2, def.id());
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement("INSERT INTO tea_achievement_defs "
                        + "(game_id, achievement, icon, name, description, position) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, def.gameId().value());
                    ins.setString(2, def.id());
                    ins.setString(3, def.icon().name());
                    ins.setString(4, name);
                    ins.setString(5, desc);
                    ins.setInt(6, position);
                    ins.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not store achievement definition " + def.id(), e);
        }
    }

    private volatile java.util.List<Stored> storedCache;
    private volatile long storedAt;

    /** {@link #stored()} as last read, refreshed in the background at most once a minute; empty until the first read. */
    public java.util.List<Stored> storedCached() {
        java.util.List<Stored> cached = storedCache;
        if ((cached == null || System.currentTimeMillis() - storedAt > 60_000) && storedLoading.compareAndSet(false, true)) {
            try {
                io.execute(() -> {
                    try {
                        storedCache = stored();
                        storedAt = System.currentTimeMillis();
                    } finally {
                        storedLoading.set(false);
                    }
                });
            } catch (RejectedExecutionException closed) {
                storedLoading.set(false);
            }
        }
        return cached == null ? java.util.List.of() : cached;
    }

    /** Includes games that are only stored by other servers. */
    public java.util.List<String> ids(GameId game) {
        java.util.List<String> local = forGame(game).stream().map(AchievementDefinition::id).toList();
        if (!local.isEmpty()) {
            return local;
        }
        return storedCached().stream().filter(a -> a.gameId().equals(game)).map(Stored::id).toList();
    }

    /** In each game's registration order. */
    public java.util.List<Stored> stored() {
        java.util.List<Stored> out = new java.util.ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT game_id, achievement, icon, name, description "
                     + "FROM tea_achievement_defs ORDER BY game_id, position");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                org.bukkit.Material icon = org.bukkit.Material.matchMaterial(rs.getString("icon"));
                out.add(new Stored(GameId.of(rs.getString("game_id")), rs.getString("achievement"),
                        icon == null ? org.bukkit.Material.GOLD_INGOT : icon,
                        rs.getString("name"), rs.getString("description")));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not read achievement definitions", e);
        }
        return out;
    }

    @Override
    public boolean unlock(Player player, GameId gameId, String achievementId) {
        if (player == null) {
            return false;
        }
        Unlocked known = unlocked.getIfPresent(player.getUniqueId());
        if (known != null && known.keys().contains(key(gameId, achievementId))
                || isUnlocked(player.getUniqueId(), gameId, achievementId)) {
            return false;
        }
        String sql = "INSERT INTO tea_achievements (uuid, game_id, achievement, unlocked_at) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, gameId.value());
            ps.setString(3, achievementId);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Likely a duplicate-key race: someone already had it. Don't notify.
            logger.log(Level.FINE, "Could not insert achievement " + achievementId, e);
            return false;
        }
        remember(player.getUniqueId(), gameId, achievementId);
        notifyUnlock(player, gameId, achievementId);
        if (unlockListener != null) {
            AchievementDefinition def = definitions.get(key(gameId, achievementId));
            String name = def == null ? achievementId
                    : messages.renderPlainText(messages.defaultLocale(), def.nameKey(), Map.of());
            unlockListener.accept(player.getName(), gameId, name);
        }
        return true;
    }

    @Override
    public boolean isUnlocked(UUID playerUuid, GameId gameId, String achievementId) {
        String sql = "SELECT 1 FROM tea_achievements WHERE uuid = ? AND game_id = ? AND achievement = ?";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, gameId.value());
            ps.setString(3, achievementId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    remember(playerUuid, gameId, achievementId);
                    return true;
                }
                return false;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read achievement state for " + playerUuid, e);
            return false;
        }
    }

    /** Non-blocking: the last loaded state, or false while the player's achievements load in the background. */
    public boolean isUnlockedCached(UUID playerUuid, GameId gameId, String achievementId) {
        Unlocked known = unlocked.getIfPresent(playerUuid);
        if (known == null || System.currentTimeMillis() - known.loadedAt() > REFRESH_MS) {
            loadAsync(playerUuid);
        }
        return known != null && known.keys().contains(key(gameId, achievementId));
    }

    public void loadAsync(UUID playerUuid) {
        if (!loading.add(playerUuid)) {
            return;
        }
        try {
            io.execute(() -> {
                try {
                    Set<String> keys = loadUnlocked(playerUuid);
                    if (keys != null) {
                        // Keeps unlocks recorded while this read ran.
                        unlocked.asMap().merge(playerUuid, new Unlocked(keys, System.currentTimeMillis()),
                                (old, fresh) -> {
                                    fresh.keys().addAll(old.keys());
                                    return fresh;
                                });
                    }
                } finally {
                    loading.remove(playerUuid);
                }
            });
        } catch (RejectedExecutionException closed) {
            loading.remove(playerUuid);
        }
    }

    private Set<String> loadUnlocked(UUID playerUuid) {
        Set<String> keys = ConcurrentHashMap.newKeySet();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT game_id, achievement FROM tea_achievements WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1) + ":" + rs.getString(2));
                }
            }
            return keys;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load achievements of " + playerUuid, e);
            return null;
        }
    }

    private void remember(UUID playerUuid, GameId gameId, String achievementId) {
        Unlocked known = unlocked.getIfPresent(playerUuid);
        if (known != null) {
            known.keys().add(key(gameId, achievementId));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadAsync(event.getPlayer().getUniqueId());
    }

    public void close() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Collection<AchievementDefinition> forGame(GameId gameId) {
        return definitions.values().stream()
                .filter(def -> def.gameId().equals(gameId))
                .toList();
    }

    @Override
    public Optional<AchievementDefinition> definition(GameId gameId, String achievementId) {
        return Optional.ofNullable(definitions.get(key(gameId, achievementId)));
    }

    private void notifyUnlock(Player player, GameId gameId, String achievementId) {
        AchievementDefinition def = definitions.get(key(gameId, achievementId));
        String nameKey = def == null ? "" : def.nameKey();
        String name = nameKey.isEmpty() ? achievementId
                : messages.renderLegacy("en_US", nameKey, Map.of());
        messages.send(player, "core.achievement.chat", Map.of("name", name));
        if (!nameKey.isEmpty()) {
            messages.showTitle(player, "core.achievement.title", nameKey, 5, 40, 10);
        }
        me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().sounds().play(player, "UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f);
    }

    private static String key(GameId gameId, String id) {
        return gameId.value() + ":" + id;
    }
}
