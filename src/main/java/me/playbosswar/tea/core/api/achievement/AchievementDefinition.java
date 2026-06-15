package me.playbosswar.tea.core.api.achievement;

import java.util.Objects;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Material;

/**
 * Declarative description of an achievement a game can award.
 *
 * <p>
 * Games register their definitions once on enable via
 * {@link AchievementService#register(AchievementDefinition)} and then award them
 * by id with {@link AchievementService#unlock}. The id is unique within a game;
 * the same id may be reused across different games. Display text is resolved
 * through the {@link me.playbosswar.tea.core.api.message.MessageService} at
 * notify time, so {@code nameKey} and {@code descKey} are translation keys, not
 * literals.
 *
 * @param gameId  game the achievement belongs to; never {@code null}.
 * @param id      stable id unique within the game; never {@code null}.
 * @param nameKey translation key for the display name; never {@code null}.
 * @param descKey translation key for the description; never {@code null}.
 * @param icon    material shown in achievement UIs; never {@code null}.
 */
public record AchievementDefinition(
        GameId gameId,
        String id,
        String nameKey,
        String descKey,
        Material icon) {

    public AchievementDefinition {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(descKey, "descKey");
        icon = icon == null ? Material.GOLD_INGOT : icon;
    }

    /** Convenience factory defaulting the icon to a gold ingot. */
    public static AchievementDefinition of(GameId gameId, String id, String nameKey, String descKey) {
        return new AchievementDefinition(gameId, id, nameKey, descKey, Material.GOLD_INGOT);
    }
}
