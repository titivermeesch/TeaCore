package me.playbosswar.tea.core.api.achievement;

import java.util.Objects;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Material;

/**
 * An achievement a game registers on enable with
 * {@link AchievementService#register(AchievementDefinition)}.
 *
 * @param id      unique within the game; may repeat across games
 * @param nameKey translation key, resolved when the player is notified
 * @param descKey translation key, resolved when the player is notified
 * @param icon    {@code null} defaults to a gold ingot
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

    /** Gold ingot icon. */
    public static AchievementDefinition of(GameId gameId, String id, String nameKey, String descKey) {
        return new AchievementDefinition(gameId, id, nameKey, descKey, Material.GOLD_INGOT);
    }
}
