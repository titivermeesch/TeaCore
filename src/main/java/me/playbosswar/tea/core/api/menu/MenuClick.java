package me.playbosswar.tea.core.api.menu;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Context for a {@link MenuAction}.
 *
 * @param player the player who clicked.
 * @param menuId the menu the click happened in.
 * @param args   the menu's open arguments.
 * @param values the clicked item's values (menu arguments plus the entry's).
 * @param type   the click type (left, right, shift-left, ...).
 * @param menus  the menu service, e.g. to open another menu.
 */
public record MenuClick(Player player, String menuId, Map<String, String> args,
                        Map<String, String> values, ClickType type, MenuService menus) {

    /** Re-renders the menu the player is looking at (e.g. after a purchase). */
    public void refresh() {
        menus.open(player, menuId, args);
    }
}
