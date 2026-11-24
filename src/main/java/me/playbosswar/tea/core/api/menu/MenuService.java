package me.playbosswar.tea.core.api.menu;

import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Inventory menus defined in YAML: a layout pattern, fixed items, and list
 * areas a {@link MenuSource} fills with one item per entry, paged
 * automatically. Clicks run {@link MenuAction}s such as {@code close},
 * {@code back}, {@code open-menu:<id> key=value}, {@code command:<cmd>} or
 * game-registered ones.
 *
 * <p>Menus can be turned off server-wide ({@code features.menus}), per game
 * ({@code features.games.<id>.menus}) and per file ({@code enabled: false}).
 */
public interface MenuService {

    /**
     * Loads a menu file from {@code owner}'s jar, extracting it to the data
     * folder on first run so admins can edit it. The id is
     * {@code <owner name lower-case>:<file name without .yml>}, or just the file
     * name for TeaCore's own menus.
     *
     * @param resourcePath e.g. {@code menus/shop.yml}
     * @return the registered menu id
     */
    String registerMenu(Plugin owner, String resourcePath);

    /** Namespace ids with the game id, e.g. {@code fireball:shop}. */
    void registerSource(String id, MenuSource source);

    /** Namespace ids with the game id, e.g. {@code fireball:buy}. */
    void registerAction(String id, MenuAction action);

    /**
     * @param args available as {@code {key}} placeholders and passed to sources (e.g. {@code game=fireball})
     * @return {@code false} if the menu is unknown or turned off
     */
    boolean open(Player player, String menuId, Map<String, String> args);

    boolean isEnabled(String menuId);

    Set<String> menuIds();

    /** Re-reads every menu file. Also runs on {@code /tea reload}. */
    void reload();
}
