package me.playbosswar.tea.core.impl.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.Nullable;

/**
 * A menu file parsed into its layout:
 *
 * <pre>
 * enabled: true
 * permission: ""             # optional; needed to open it with /menu
 * bedrock: true              # Bedrock players (Floodgate) get it as a form; false = chest
 * title: "&lt;primary&gt;Play"
 * rows: 4
 * refresh-ticks: 20          # re-render while open (0 = never)
 * pattern:                   # one string per row, one character per slot
 *   - "#########"
 *   - "#GGGGGGG#"
 * items:                     # fixed items, keyed by pattern character
 *   '#': { material: GRAY_STAINED_GLASS_PANE, name: " " }
 *   'X': { material: BARRIER, name: "&lt;error&gt;Close", action: close }
 * lists:                     # list areas filled by a source, paged
 *   'G':
 *     source: games
 *     item: { material: "{icon}", name: "{game_name}", action: "open-menu:join-maps game={game_id}" }
 *     empty: { material: BARRIER, name: "&lt;muted&gt;Nothing here" }
 * </pre>
 *
 * Item keys: {@code material}, {@code name}, {@code lore}, {@code amount},
 * {@code glow}, {@code view-if}, {@code skull-owner}, {@code custom-model-data},
 * and actions as {@code action}/{@code actions} (any click) or
 * {@code left-actions}/{@code right-actions}/{@code shift-actions}, and the more
 * specific {@code shift-left-actions}/{@code shift-right-actions}.
 */
record MenuDef(boolean enabled, @Nullable String permission, boolean bedrock, String title, int rows, int refreshTicks, List<String> pattern,
               Map<Character, ItemDef> items, Map<Character, ListDef> lists) {

    char charAt(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row >= pattern.size()) {
            return ' ';
        }
        String line = pattern.get(row);
        return col < line.length() ? line.charAt(col) : ' ';
    }

    static MenuDef parse(ConfigurationSection yaml) {
        int rows = Math.max(1, Math.min(6, yaml.getInt("rows", 3)));
        Map<Character, ItemDef> items = new HashMap<>();
        ConfigurationSection itemSection = yaml.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                ConfigurationSection s = itemSection.getConfigurationSection(key);
                if (s != null && !key.isEmpty()) {
                    items.put(key.charAt(0), ItemDef.parse(s));
                }
            }
        }
        Map<Character, ListDef> lists = new HashMap<>();
        ConfigurationSection listSection = yaml.getConfigurationSection("lists");
        if (listSection != null) {
            for (String key : listSection.getKeys(false)) {
                ConfigurationSection s = listSection.getConfigurationSection(key);
                if (s != null && !key.isEmpty() && s.isConfigurationSection("item")) {
                    lists.put(key.charAt(0), new ListDef(s.getString("source", ""),
                            ItemDef.parse(s.getConfigurationSection("item")),
                            s.isConfigurationSection("empty") ? ItemDef.parse(s.getConfigurationSection("empty")) : null));
                }
            }
        }
        return new MenuDef(yaml.getBoolean("enabled", true), yaml.getString("permission"),
                yaml.getBoolean("bedrock", true), yaml.getString("title", ""), rows,
                Math.max(0, yaml.getInt("refresh-ticks", 0)), List.copyOf(yaml.getStringList("pattern")),
                Map.copyOf(items), Map.copyOf(lists));
    }

    record ListDef(String source, ItemDef item, @Nullable ItemDef empty) { }

    /** One item template. Text fields may contain {placeholders}, theme tags and %papi%. */
    record ItemDef(String material, @Nullable String name, List<String> lore, @Nullable String amount,
                   @Nullable String glow, @Nullable String viewIf, @Nullable String skullOwner,
                   @Nullable Integer customModelData, List<String> actions, List<String> leftActions,
                   List<String> rightActions, List<String> shiftActions, List<String> shiftLeftActions,
                   List<String> shiftRightActions, boolean showWithoutHistory) {

        static ItemDef parse(ConfigurationSection s) {
            return new ItemDef(
                    s.getString("material", "STONE"),
                    s.getString("name"),
                    List.copyOf(s.getStringList("lore")),
                    s.isSet("amount") ? s.getString("amount") : null,
                    s.isSet("glow") ? s.getString("glow") : null,
                    s.getString("view-if"),
                    s.getString("skull-owner"),
                    s.isInt("custom-model-data") ? s.getInt("custom-model-data") : null,
                    actionList(s, "action", "actions"),
                    actionList(s, null, "left-actions"),
                    actionList(s, null, "right-actions"),
                    actionList(s, null, "shift-actions"),
                    actionList(s, null, "shift-left-actions"),
                    actionList(s, null, "shift-right-actions"),
                    s.getBoolean("show-without-history", false));
        }

        private static List<String> actionList(ConfigurationSection s, @Nullable String single, String many) {
            List<String> out = new ArrayList<>();
            if (single != null && s.isString(single)) {
                out.add(s.getString(single));
            }
            if (s.isList(many)) {
                out.addAll(s.getStringList(many));
            } else if (s.isString(many)) {
                out.add(s.getString(many));
            }
            return List.copyOf(out);
        }

        boolean clickable() {
            return !(actions.isEmpty() && leftActions.isEmpty() && rightActions.isEmpty() && shiftActions.isEmpty()
                    && shiftLeftActions.isEmpty() && shiftRightActions.isEmpty());
        }

        /** The specific list if set, else the generic one. */
        List<String> actionsFor(ClickType click) {
            if (click == ClickType.SHIFT_LEFT && !shiftLeftActions.isEmpty()) {
                return shiftLeftActions;
            }
            if (click == ClickType.SHIFT_RIGHT && !shiftRightActions.isEmpty()) {
                return shiftRightActions;
            }
            if (click.isShiftClick() && !shiftActions.isEmpty()) {
                return shiftActions;
            }
            if (click.isRightClick() && !rightActions.isEmpty()) {
                return rightActions;
            }
            if (click.isLeftClick() && !leftActions.isEmpty()) {
                return leftActions;
            }
            return actions;
        }
    }
}
