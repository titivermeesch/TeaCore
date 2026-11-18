package me.playbosswar.tea.core.api.menu;

import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Inventory menus defined in YAML.
 *
 * <p>
 * A menu file describes the layout (title, rows, a character pattern), fixed
 * items, and list areas that a {@link MenuSource} fills with one item per entry
 * (games, maps, kits, shop items, ...), paged automatically. Clicks run
 * {@link MenuAction}s such as {@code close}, {@code back},
 * {@code open-menu:<id> key=value}, {@code command:<cmd>} or anything a game
 * registers. TeaCore's own menus live in {@code plugins/TeaCore/menus/}; a game
 * ships its menus in its jar and registers them with {@link #registerMenu}, after
 * which admins edit the copy in the game's data folder.
 *
 * <p>
 * Menus can be turned off server-wide ({@code features.menus}), per game
 * ({@code features.games.<id>.menus}) and per file ({@code enabled: false}).
 */
public interface MenuService {

    /**
     * Loads (and on first run extracts) a menu file shipped in {@code owner}'s jar.
     * The menu id is {@code <owner name lower-case>:<file name without .yml>}
     * unless the file lives in TeaCore, where it is just the file name.
     *
     * @param owner        the plugin that ships the file.
     * @param resourcePath path inside the jar, e.g. {@code menus/shop.yml}.
     * @return the menu id it was registered under.
     */
    String registerMenu(Plugin owner, String resourcePath);

    /** Registers a list source under {@code id} (namespace games ids, e.g. {@code fireball:shop}). */
    void registerSource(String id, MenuSource source);

    /** Registers a click action under {@code id} (e.g. {@code fireball:buy}). */
    void registerAction(String id, MenuAction action);

    /**
     * Opens a menu.
     *
     * @param args values available to the menu as {@code {key}} placeholders and
     *             passed to its sources (e.g. {@code game=fireball}).
     * @return {@code false} if the menu is unknown or turned off.
     */
    boolean open(Player player, String menuId, Map<String, String> args);

    /** Whether a menu exists and is turned on. */
    boolean isEnabled(String menuId);

    /** Ids of every loaded menu. */
    Set<String> menuIds();

    /** Re-reads every menu file (also done on {@code /tea reload}). */
    void reload();
}
