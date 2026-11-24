package me.playbosswar.tea.core.api.kit;

import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A selectable loadout offered during the waiting phase.
 *
 * <p>
 * A priced kit is paid for once in the kit picker and then stays unlocked.
 *
 * @param kitId          unique within the game
 * @param displayNameKey translation key
 * @param descriptionKey translation key
 * @param items          keyed by {@code PlayerInventory} slot: {@code 0-8}
 *                       hotbar, {@code 9-35} main, {@code 36-39} armor
 *                       (boots to helmet), {@code 40} off-hand; {@code null}
 *                       means none
 * @param cost           one-time unlock cost in Vault currency units;
 *                       {@code 0} is free, negative values become {@code 0}
 */
public record Kit(
        GameId gameId,
        String kitId,
        String displayNameKey,
        String descriptionKey,
        Material icon,
        List<AbilityBinding> abilities,
        Map<Integer, ItemStack> items,
        long cost) {

    public Kit {
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        items = items == null ? Map.of() : Map.copyOf(items);
        if (cost < 0) cost = 0;
    }

    /** A free kit with no starter items. */
    public Kit(GameId gameId, String kitId, String displayNameKey, String descriptionKey,
            Material icon, List<AbilityBinding> abilities) {
        this(gameId, kitId, displayNameKey, descriptionKey, icon, abilities, Map.of(), 0);
    }

    /** A free kit. */
    public Kit(GameId gameId, String kitId, String displayNameKey, String descriptionKey,
            Material icon, List<AbilityBinding> abilities,
            Map<Integer, ItemStack> items) {
        this(gameId, kitId, displayNameKey, descriptionKey, icon, abilities, items, 0);
    }
}
