package me.playbosswar.tea.core.api.achievement;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.entity.Player;

/**
 * Database-backed achievements. Unlocks are persisted and idempotent; TeaCore
 * shows the unlock (title, sound, chat) itself.
 */
public interface AchievementService {

    /** Replaces any definition already registered with the same {@code (gameId, id)}. */
    void register(AchievementDefinition definition);

    /**
     * @return {@code true} if newly unlocked and the player was notified,
     *         {@code false} if they already had it
     */
    boolean unlock(Player player, GameId gameId, String achievementId);

    boolean isUnlocked(UUID playerUuid, GameId gameId, String achievementId);

    /** @return in registration order */
    Collection<AchievementDefinition> forGame(GameId gameId);

    Optional<AchievementDefinition> definition(GameId gameId, String achievementId);
}
