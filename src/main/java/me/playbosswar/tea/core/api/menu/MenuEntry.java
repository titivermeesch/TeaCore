package me.playbosswar.tea.core.api.menu;

import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of a list area.
 *
 * @param values {@code {placeholder}} values for the item template and its
 *               actions, e.g. {@code game_id -> fireball}.
 * @param icon   an item to use as the base (material, model, enchant glint)
 *               instead of the template's {@code material}; the template's name
 *               and lore are still applied on top. May be {@code null}.
 */
public record MenuEntry(Map<String, String> values, @Nullable ItemStack icon) {

    public MenuEntry {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static MenuEntry of(Map<String, String> values) {
        return new MenuEntry(values, null);
    }
}
