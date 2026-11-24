package me.playbosswar.tea.core.impl.kit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class KitSelectorGui {

    private static final int SIZE = 27;
    private static final int FIRST_SLOT = 10; // start of row 2, leaving edges empty
    private static final int LAST_SLOT = 16;

    private final Plugin plugin;
    private final KitRegistry kitRegistry;
    private final MessageService messageService;
    private final EconomyService economyService;

    public KitSelectorGui(Plugin plugin, KitRegistry kitRegistry, MessageService messageService,
            EconomyService economyService) {
        this.plugin = plugin;
        this.kitRegistry = kitRegistry;
        this.messageService = messageService;
        this.economyService = economyService;
    }

    public static String kitIdOf(ItemStack item) {
        return me.playbosswar.tea.core.impl.platform.TeaTags.get(item, "kit_id");
    }

    @SuppressWarnings("deprecation")
    public boolean open(Player player, GameId gameId) {
        Collection<Kit> kits = kitRegistry.kitsFor(gameId);
        if (kits.isEmpty()) {
            messageService.send(player, "core.kit-picker.none", Map.of("game", gameId.value()));
            return false;
        }
        String locale = messageService.resolveLocale(player);
        String title = messageService.renderLegacyNoPrefix(locale, "core.kit-picker.title",
                Map.of("game", gameId.value()));

        KitSelectorHolder holder = new KitSelectorHolder(gameId);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        Kit current = kitRegistry.selectedKit(player, gameId).orElse(null);
        int slot = FIRST_SLOT;
        for (Kit kit : kits) {
            if (slot > LAST_SLOT)
                break;
            inv.setItem(slot++, buildKitItem(player, kit, current, locale));
        }

        player.openInventory(inv);
        return true;
    }

    private ItemStack buildKitItem(Player player, Kit kit, Kit currentSelection, String locale) {
        boolean hasCost = kit.cost() > 0;
        boolean unlocked = kitRegistry.isUnlocked(player.getUniqueId(), kit);
        boolean canAfford = !hasCost || unlocked
                || (economyService != null && economyService.has(player.getUniqueId(), kit.cost()));
        boolean locked = hasCost && !unlocked && !canAfford;

        Material material = locked ? Material.BARRIER
                : (kit.icon() == null ? Material.PAPER : kit.icon());
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null)
            return stack;

        String name = messageService.renderLegacyNoPrefix(locale, kit.displayNameKey(), Map.of());
        if (locked) {
            name = messageService.renderLegacyNoPrefix(locale, "core.kit-picker.locked-prefix",
                    Map.of("name", name));
        }
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>(
                messageService.renderLegacyLines(locale, kit.descriptionKey(), Map.of()));
        if (hasCost) {
            String costLine;
            if (unlocked) {
                costLine = messageService.renderLegacyNoPrefix(locale, "core.kit-picker.cost-unlocked",
                        Map.of("cost", formatAmount(kit.cost())));
            } else if (canAfford) {
                costLine = messageService.renderLegacyNoPrefix(locale, "core.kit-picker.cost",
                        Map.of("cost", formatAmount(kit.cost())));
            } else {
                costLine = messageService.renderLegacyNoPrefix(locale, "core.kit-picker.cost-insufficient",
                        Map.of(
                                "cost", formatAmount(kit.cost()),
                                "balance", formatAmount(economyService == null ? 0
                                        : (long) economyService.balance(player.getUniqueId()))));
            }
            lore.add(costLine);
        }
        meta.setLore(lore);

        if (currentSelection != null && currentSelection.kitId().equals(kit.kitId())) {
            meta.addEnchant(me.playbosswar.tea.core.impl.platform.Compat.glintEnchantment(), 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        stack.setItemMeta(meta);
        return me.playbosswar.tea.core.impl.platform.TeaTags.set(stack, "kit_id", kit.kitId());
    }

    private String formatAmount(long amount) {
        if (economyService == null || !economyService.isAvailable()) {
            return String.valueOf(amount);
        }
        return economyService.format(amount);
    }
}
