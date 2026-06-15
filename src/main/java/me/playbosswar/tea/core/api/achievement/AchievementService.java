package me.playbosswar.tea.core.api.achievement;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.entity.Player;

/**
 * Shared, DB-backed achievement registry and award service.
 *
 * <p>
 * Games register their {@link AchievementDefinition}s on enable and award them
 * by id during play. Unlocks are persisted per {@code (player, game, id)} and
 * are idempotent: a repeat unlock is a no-op and produces no notification.
 * TeaCore owns the unlock presentation (title, sound, chat) so every game gets
 * a consistent feel.
 */
public interface AchievementService {

    /**
     * Registers an achievement definition.
     *
     * <p>Re-registering the same {@code (gameId, id)} replaces the prior
     * definition. Call once per achievement in {@code onEnable}.
     *
     * @param definition the definition to register; must not be {@code null}.
     */
    void register(AchievementDefinition definition);

    /**
     * Awards an achievement to a player if they do not already hold it.
     *
     * @param player        recipient; must not be {@code null}.
     * @param gameId        owning game; must not be {@code null}.
     * @param achievementId achievement id within the game; must not be {@code null}.
     * @return {@code true} if it was newly unlocked (and the player notified),
     *         {@code false} if they already had it.
     */
    boolean unlock(Player player, GameId gameId, String achievementId);

    /**
     * Tests whether a player has unlocked an achievement.
     *
     * @param playerUuid    player to check; must not be {@code null}.
     * @param gameId        owning game; must not be {@code null}.
     * @param achievementId achievement id within the game; must not be {@code null}.
     * @return {@code true} if the player has it.
     */
    boolean isUnlocked(UUID playerUuid, GameId gameId, String achievementId);

    /**
     * Returns the registered definitions for a game, in registration order.
     *
     * @param gameId game to list; must not be {@code null}.
     * @return the definitions, never {@code null}.
     */
    Collection<AchievementDefinition> forGame(GameId gameId);

    /**
     * Looks up a registered definition.
     *
     * @param gameId        owning game; must not be {@code null}.
     * @param achievementId achievement id; must not be {@code null}.
     * @return the definition, or empty if none is registered.
     */
    Optional<AchievementDefinition> definition(GameId gameId, String achievementId);
}
