package me.playbosswar.tea.core.api.menu;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Context for a {@link MenuAction}.
 *
 * @param args   the arguments the menu was opened with
 * @param values the menu arguments plus the clicked entry's values
 */
public record MenuClick(Player player, String menuId, Map<String, String> args,
                        Map<String, String> values, ClickType type, MenuService menus) {

    /** Re-renders the menu the player is looking at (e.g. after a purchase). */
    public void refresh() {
        menus.open(player, menuId, args);
    }
}
