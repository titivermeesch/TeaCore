package me.playbosswar.tea.core.impl.achievement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.achievement.AchievementDefinition;
import me.playbosswar.tea.core.api.achievement.AchievementService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * DB-backed {@link AchievementService}.
 *
 * <p>
 * Definitions are held in memory (registered on game enable); unlock state lives
 * in {@code tea_achievements}, keyed by {@code (uuid, game_id, achievement)}.
 * Unlocking inserts a row and notifies the player once; the primary key makes a
 * repeat unlock a no-op. The notification (chat line, title, sound) is rendered
 * through {@link MessageService} so it is themed and localized like everything
 * else in TeaCore.
 */
public final class AchievementServiceImpl implements AchievementService {

    private final DataSource dataSource;
    private final Logger logger;
    private final MessageService messages;
    /** "<gameId>:<id>" -> definition. */
    private final Map<String, AchievementDefinition> definitions = new ConcurrentHashMap<>();

    public AchievementServiceImpl(DataSource dataSource, Logger logger, MessageService messages) {
        this.dataSource = dataSource;
        this.logger = logger;
        this.messages = messages;
    }

    @Override
    public void register(AchievementDefinition definition) {
        definitions.put(key(definition.gameId(), definition.id()), definition);
    }

    @Override
    public boolean unlock(Player player, GameId gameId, String achievementId) {
        if (player == null || isUnlocked(player.getUniqueId(), gameId, achievementId)) {
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
        notifyUnlock(player, gameId, achievementId);
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
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read achievement state for " + playerUuid, e);
            return false;
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
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private static String key(GameId gameId, String id) {
        return gameId.value() + ":" + id;
    }
}
