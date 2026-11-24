package me.playbosswar.tea.core.impl.kit;

import java.util.Map;
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class KitSelectorListener implements Listener {

    private final Plugin plugin;
    private final KitSelectorGui gui;
    private final KitRegistry kitRegistry;
    private final MessageService messageService;
    private final EconomyService economyService;

    public KitSelectorListener(Plugin plugin, KitSelectorGui gui, KitRegistry kitRegistry,
            MessageService messageService, EconomyService economyService) {
        this.plugin = plugin;
        this.gui = gui;
        this.kitRegistry = kitRegistry;
        this.messageService = messageService;
        this.economyService = economyService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitSelectorHolder holder))
            return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory())
            return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta())
            return;
        String kitId = KitSelectorGui.kitIdOf(clicked);
        if (kitId == null)
            return;

        Player player = (Player) event.getWhoClicked();
        Kit kit = kitRegistry.kit(holder.gameId(), kitId).orElse(null);
        if (kit == null) return;

        if (kit.cost() > 0 && !kitRegistry.isUnlocked(player.getUniqueId(), kit)) {
            if (economyService == null || !economyService.isAvailable()) {
                messageService.send(player, "core.kit-picker.economy-unavailable", Map.of());
                return;
            }
            if (!economyService.has(player.getUniqueId(), kit.cost())) {
                messageService.send(player, "core.kit-picker.insufficient", Map.of(
                        "cost", economyService.format(kit.cost()),
                        "balance", economyService.format(economyService.balance(player.getUniqueId()))));
                return;
            }
            if (!economyService.withdraw(player.getUniqueId(), kit.cost())) {
                messageService.send(player, "core.kit-picker.withdraw-failed", Map.of());
                return;
            }
            if (!kitRegistry.unlock(player.getUniqueId(), kit)) {
                // Refund so we don't charge for a failed unlock record.
                economyService.deposit(player.getUniqueId(), kit.cost());
                messageService.send(player, "core.kit-picker.unlock-failed", Map.of());
                return;
            }
            messageService.send(player, "core.kit-picker.purchased", Map.of(
                    "kit", kitId,
                    "cost", economyService.format(kit.cost())));
        }

        kitRegistry.selectKit(player, holder.gameId(), kitId);
        player.closeInventory();
        messageService.send(player, "core.kit-picker.selected", Map.of(
                "kit", kitId,
                "game", holder.gameId().value()));
    }
}
