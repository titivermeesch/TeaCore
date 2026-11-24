package me.playbosswar.tea.core.impl.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.stats.StatsSchema;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CoreMenus {

    private final TeaCoreAPI core;
    private final GameDisplay display;

    public CoreMenus(TeaCoreAPI core, GameDisplay display) {
        this.core = core;
        this.display = display;
    }

    public void register(MenuService menus) {
        menus.registerSource("kits", this::kits);
        menus.registerAction("kit-select", (click, arg) -> {
            String[] parts = arg.split("\\s+");
            if (parts.length >= 2) {
                selectKit(click.player(), GameId.of(parts[0]), parts[1]);
                click.refresh();
            }
        });
        menus.registerSource("stats-games", this::statsGames);
    }

    private List<MenuEntry> kits(Player viewer, Map<String, String> args) {
        String gameArg = args.get("game");
        if (gameArg == null) {
            return List.of();
        }
        GameId game = GameId.of(gameArg);
        MessageService ms = core.messageService();
        String locale = ms.resolveLocale(viewer);
        EconomyService economy = core.economy();
        Kit selected = core.kits().selectedKit(viewer, game).orElse(null);
        List<MenuEntry> out = new ArrayList<>();
        for (Kit kit : core.kits().kitsFor(game)) {
            boolean hasCost = kit.cost() > 0;
            boolean unlocked = core.kits().isUnlocked(viewer.getUniqueId(), kit);
            boolean canAfford = !hasCost || unlocked
                    || (economy != null && economy.isAvailable() && economy.has(viewer.getUniqueId(), kit.cost()));
            boolean locked = hasCost && !unlocked && !canAfford;
            String status = "";
            if (hasCost) {
                String key = unlocked ? "core.kit-picker.cost-unlocked"
                        : canAfford ? "core.kit-picker.cost" : "core.kit-picker.cost-insufficient";
                status = ms.renderLegacyNoPrefix(locale, key, Map.of(
                        "cost", format(economy, kit.cost()),
                        "balance", format(economy, economy == null || !economy.isAvailable() ? 0
                                : (long) economy.balance(viewer.getUniqueId()))));
            }
            Map<String, String> values = new HashMap<>();
            values.put("game", game.value());
            values.put("game_name", display.name(locale, game));
            values.put("kit_id", kit.kitId());
            values.put("kit_name", ms.renderLegacyNoPrefix(locale, kit.displayNameKey(), Map.of()));
            values.put("kit_desc", String.join("\n", ms.renderLegacyLines(locale, kit.descriptionKey(), Map.of())));
            values.put("kit_cost", format(economy, kit.cost()));
            values.put("kit_status", status);
            values.put("selected", String.valueOf(selected != null && selected.kitId().equals(kit.kitId())));
            values.put("locked", String.valueOf(locked));
            values.put("unlocked", String.valueOf(!hasCost || unlocked));
            Material icon = locked ? Material.BARRIER : kit.icon() == null ? Material.PAPER : kit.icon();
            out.add(new MenuEntry(values, new ItemStack(icon)));
        }
        return out;
    }

    public void selectKit(Player player, GameId game, String kitId) {
        Kit kit = core.kits().kit(game, kitId).orElse(null);
        if (kit == null) {
            return;
        }
        MessageService ms = core.messageService();
        EconomyService economy = core.economy();
        if (kit.cost() > 0 && !core.kits().isUnlocked(player.getUniqueId(), kit)) {
            if (economy == null || !economy.isAvailable()) {
                ms.send(player, "core.kit-picker.economy-unavailable", Map.of());
                return;
            }
            if (!economy.has(player.getUniqueId(), kit.cost())) {
                ms.send(player, "core.kit-picker.insufficient", Map.of(
                        "cost", economy.format(kit.cost()),
                        "balance", economy.format(economy.balance(player.getUniqueId()))));
                return;
            }
            if (!economy.withdraw(player.getUniqueId(), kit.cost())) {
                ms.send(player, "core.kit-picker.withdraw-failed", Map.of());
                return;
            }
            if (!core.kits().unlock(player.getUniqueId(), kit)) {
                economy.deposit(player.getUniqueId(), kit.cost());
                ms.send(player, "core.kit-picker.unlock-failed", Map.of());
                return;
            }
            ms.send(player, "core.kit-picker.purchased", Map.of("kit", kitId, "cost", economy.format(kit.cost())));
        }
        core.kits().selectKit(player, game, kitId);
        ms.send(player, "core.kit-picker.selected", Map.of("kit", kitId, "game", game.value()));
    }

    private List<MenuEntry> statsGames(Player viewer, Map<String, String> args) {
        String raw = args.get("target_uuid");
        if (raw == null) {
            return List.of();
        }
        UUID target = UUID.fromString(raw);
        MessageService ms = core.messageService();
        String locale = ms.resolveLocale(viewer);
        List<MenuEntry> out = new ArrayList<>();
        for (StatsSchema schema : core.stats().allSchemas()) {
            Map<String, String> entries = core.stats().providerFor(schema).fetch(target);
            List<String> lines = new ArrayList<>();
            if (entries == null || entries.isEmpty()) {
                lines.add(ms.renderLegacyNoPrefix(locale, "core.stats.gui.game.empty", Map.of()));
            } else {
                entries.forEach((label, value) -> lines.add(ms.renderLegacyNoPrefix(locale,
                        "core.stats.gui.game.entry", Map.of("label", label, "value", value))));
            }
            String name = display.name(locale, schema.gameId());
            String rating = "-";
            String rank = "-";
            var ratings = core instanceof me.playbosswar.tea.core.impl.TeaCorePlugin tc ? tc.ratings() : null;
            if (ratings != null && ratings.enabled(schema.gameId())) {
                var r = ratings.read(target, schema.gameId());
                if (r.games() > 0) {
                    rating = String.valueOf(r.rating());
                    int place = ratings.rank(target, schema.gameId());
                    rank = place > 0 ? String.valueOf(place) : "-";
                    lines.add(ms.renderLegacyNoPrefix(locale, "core.stats.gui.game.rating",
                            Map.of("rating", rating, "rank", rank, "wins", String.valueOf(r.wins()),
                                    "losses", String.valueOf(r.losses()))));
                }
            }
            Map<String, String> values = new HashMap<>();
            values.put("game_id", schema.gameId().value());
            values.put("game_name", name);
            values.put("lines", String.join("\n", lines));
            values.put("has_data", String.valueOf(entries != null && !entries.isEmpty()));
            values.put("rating", rating);
            values.put("rank", rank);
            out.add(MenuEntry.of(values));
        }
        return out;
    }

    private static String format(EconomyService economy, long amount) {
        return economy == null || !economy.isAvailable() ? String.valueOf(amount) : economy.format(amount);
    }
}
