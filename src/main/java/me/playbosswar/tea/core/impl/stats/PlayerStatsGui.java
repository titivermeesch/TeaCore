package me.playbosswar.tea.core.impl.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.stats.StatsProvider;
import me.playbosswar.tea.core.api.stats.StatsRegistry;
import me.playbosswar.tea.core.api.stats.StatsSchema;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Builds + opens an inventory-based stats viewer. Top-center slot is a
 * summary item; the second row holds one paper item per game with a persisted
 * stats schema, each lore-listing the player's stat key/value pairs from that
 * game's effective {@link StatsProvider}. A close button sits at the bottom-center.
 *
 * <p>Schemas come from the shared database (see {@code tea_stats_schemas}), so
 * a lobby renders every game in the fleet — even ones whose own plugin isn't
 * loaded on this server.
 */
public final class PlayerStatsGui {

    private static final int SIZE = 27;
    private static final int SUMMARY_SLOT = 4;
    private static final int GAMES_ROW_START = 9;
    private static final int GAMES_ROW_END = 17;
    private static final int CLOSE_SLOT = 22;

    private final Plugin plugin;
    private final StatsRegistry stats;
    private final MessageService messageService;

    public PlayerStatsGui(Plugin plugin, StatsRegistry stats, MessageService messageService) {
        this.plugin = plugin;
        this.stats = stats;
        this.messageService = messageService;
    }

    @SuppressWarnings("deprecation")
    public void open(Player viewer, UUID targetUuid, String targetName) {
        String locale = messageService.resolveLocale(viewer);
        String title = messageService.renderLegacyNoPrefix(locale, "core.stats.gui.title",
                Map.of("player", targetName));

        PlayerStatsHolder holder = new PlayerStatsHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        List<StatsSchema> schemas = List.copyOf(stats.allSchemas());
        inv.setItem(SUMMARY_SLOT, buildSummary(targetName, targetUuid, locale, schemas));

        int slot = GAMES_ROW_START;
        int truncated = 0;
        for (StatsSchema schema : schemas) {
            if (slot > GAMES_ROW_END) {
                truncated++;
                continue;
            }
            inv.setItem(slot++, buildGameItem(schema, targetUuid, locale));
        }
        if (truncated > 0) {
            plugin.getLogger().info("PlayerStatsGui: " + truncated
                    + " game(s) not shown — increase the games row capacity if this is permanent.");
        }

        inv.setItem(CLOSE_SLOT, buildCloseButton(locale));
        viewer.openInventory(inv);
    }

    private ItemStack buildSummary(String playerName, UUID targetUuid, String locale,
            List<StatsSchema> schemas) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(messageService.renderLegacyNoPrefix(locale,
                "core.stats.gui.summary.name", Map.of("player", playerName)));
        int gamesWithData = 0;
        for (StatsSchema schema : schemas) {
            Map<String, String> entries = stats.providerFor(schema).fetch(targetUuid);
            if (entries != null && !entries.isEmpty()) gamesWithData++;
        }
        meta.setLore(messageService.renderLegacyLines(locale, "core.stats.gui.summary.lore",
                Map.of(
                        "with", String.valueOf(gamesWithData),
                        "total", String.valueOf(schemas.size()))));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildGameItem(StatsSchema schema, UUID targetUuid, String locale) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        // Lobby servers don't have game-plugin lang bundles loaded, so the
        // schema's displayNameKey often resolves to a missing-translation
        // marker. Fall back to a humanized form of the game id when the key
        // isn't registered anywhere - it's more readable than "<error>...".
        String displayName = messageService.hasKey(schema.displayNameKey())
                ? messageService.renderLegacyNoPrefix(locale, schema.displayNameKey(), Map.of())
                : humanize(schema.gameId().value());
        meta.setDisplayName(messageService.renderLegacyNoPrefix(locale, "core.stats.gui.game.name",
                Map.of("game", displayName)));

        List<String> lore = new ArrayList<>();
        Map<String, String> entries = stats.providerFor(schema).fetch(targetUuid);
        if (entries == null || entries.isEmpty()) {
            lore.add(messageService.renderLegacyNoPrefix(locale, "core.stats.gui.game.empty", Map.of()));
        } else {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                lore.add(messageService.renderLegacyNoPrefix(locale, "core.stats.gui.game.entry",
                        Map.of("label", e.getKey(), "value", e.getValue())));
            }
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Turns a snake_case game id into a Title-Cased label, e.g.
     * {@code "ascend"} -> {@code "Ascend"}, {@code "team_deathmatch"} ->
     * {@code "Team Deathmatch"}. Used as the lobby fallback for
     * {@link StatsSchema#displayNameKey()} when no lang bundle defines it.
     */
    private static String humanize(String id) {
        if (id == null || id.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(id.length());
        boolean nextUpper = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') {
                sb.append(' ');
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private ItemStack buildCloseButton(String locale) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(messageService.renderLegacyNoPrefix(locale, "core.stats.gui.close", Map.of()));
        stack.setItemMeta(meta);
        return stack;
    }
}
