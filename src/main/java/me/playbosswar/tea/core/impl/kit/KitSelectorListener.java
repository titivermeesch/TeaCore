package me.playbosswar.tea.core.impl.kit;

import java.util.Map;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Handles clicks inside the kit-selector GUI. Games open this GUI themselves
 * (typically via a waiting-area menu item); it is not auto-opened.
 */
public final class KitSelectorListener implements Listener {

    private final Plugin plugin;
    private final KitSelectorGui gui;
    private final KitRegistry kitRegistry;
    private final MessageService messageService;
    private final NamespacedKey kitKey;

    public KitSelectorListener(Plugin plugin, KitSelectorGui gui, KitRegistry kitRegistry,
            MessageService messageService) {
        this.plugin = plugin;
        this.gui = gui;
        this.kitRegistry = kitRegistry;
        this.messageService = messageService;
        this.kitKey = gui.kitKey();
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
        ItemMeta meta = clicked.getItemMeta();
        String kitId = meta.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (kitId == null)
            return;

        Player player = (Player) event.getWhoClicked();
        kitRegistry.selectKit(player, holder.gameId(), kitId);
        player.closeInventory();
        messageService.send(player, "core.kit-picker.selected", Map.of(
                "kit", kitId,
                "game", holder.gameId().value()));
    }
}
