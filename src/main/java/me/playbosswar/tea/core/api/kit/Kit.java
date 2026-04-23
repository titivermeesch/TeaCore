package me.playbosswar.tea.core.api.kit;

import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A selectable loadout for a game, bundling a set of {@link AbilityBinding}s
 * and an optional
 * inventory of starter items.
 *
 * <p>
 * Registered via {@link KitRegistry#registerKit(Kit)} and offered to players
 * during the
 * waiting phase. Each kit is scoped to a single {@link GameId}. The
 * {@code items} map
 * is keyed by Bukkit {@code PlayerInventory} slot indices, so slots {@code 0-8}
 * fill the
 * hotbar, {@code 9-35} fill the main inventory, {@code 36-39} are the armor
 * slots (boots,
 * leggings, chestplate, helmet) and {@code 40} is the off-hand. The map is
 * consulted by
 * {@link KitRegistry#applyItemsTo(org.bukkit.entity.Player, Kit)}.
 *
 * @param gameId         game the kit belongs to; never {@code null}.
 * @param kitId          id unique within the game; never {@code null}.
 * @param displayNameKey translation key for the kit's display name; never
 *                       {@code null}.
 * @param descriptionKey translation key for the kit's description; never
 *                       {@code null}.
 * @param icon           item material shown in selection UIs; never
 *                       {@code null}.
 * @param abilities      ability bindings applied when the kit is chosen;
 *                       defensively copied and never {@code null} after
 *                       construction.
 * @param items          starter items keyed by inventory slot index;
 *                       defensively copied, normalized to an empty map when
 *                       {@code null}, and never {@code null} after
 *                       construction.
 */
public record Kit(
        GameId gameId,
        String kitId,
        String displayNameKey,
        String descriptionKey,
        Material icon,
        List<AbilityBinding> abilities,
        Map<Integer, ItemStack> items) {

    /**
     * Canonical constructor that defensively copies {@code abilities} and
     * {@code items},
     * normalizing {@code null} collections to their empty counterparts.
     */
    public Kit {
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        items = items == null ? Map.of() : Map.copyOf(items);
    }

    /**
     * Back-compat constructor for kits that have no starter items.
     *
     * @param gameId         game the kit belongs to.
     * @param kitId          id unique within the game.
     * @param displayNameKey translation key for the display name.
     * @param descriptionKey translation key for the description.
     * @param icon           selection-UI icon material.
     * @param abilities      ability bindings applied on selection.
     */
    public Kit(GameId gameId, String kitId, String displayNameKey, String descriptionKey,
            Material icon, List<AbilityBinding> abilities) {
        this(gameId, kitId, displayNameKey, descriptionKey, icon, abilities, Map.of());
    }
}
