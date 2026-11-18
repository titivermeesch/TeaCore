package me.playbosswar.tea.core.impl.lobby.hotbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.impl.sidebar.PapiBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Lobby hotbar items from {@code lobby.hotbar.items} (e.g. a compass that opens
 * the play menu). Given on join, respawn and when leaving an arena, removed
 * when joining one, and locked in place. Needs {@code lobby.enabled} and
 * {@code features.lobby-hotbar}.
 */
public final class LobbyHotbarService implements Listener {

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final NamespacedKey key;

    public LobbyHotbarService(Plugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        this.key = new NamespacedKey(plugin, "lobby_item");
    }

    private boolean active() {
        return plugin.getConfig().getBoolean("lobby.enabled", false) && core.features().enabled("lobby-hotbar");
    }

    /** Gives the configured items unless the player is in an arena. */
    public void give(Player player) {
        if (!player.isOnline() || core.arenas().arenaOf(player.getUniqueId()).isPresent()) {
            return;
        }
        take(player); // replace items from an older config, or remove them if turned off
        if (!active()) {
            return;
        }
        List<?> items = plugin.getConfig().getList("lobby.hotbar.items", List.of());
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> raw)) {
                continue;
            }
            ConfigurationSection s = new org.bukkit.configuration.MemoryConfiguration().createSection("i", raw);
            Material material = Material.matchMaterial(s.getString("material", "COMPASS"));
            if (material == null) {
                continue;
            }
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                continue;
            }
            Map<String, String> ph = Map.of("player", player.getName());
            meta.setDisplayName(core.messageService().renderInline(
                    PapiBridge.apply(player, s.getString("name", "")), ph));
            List<String> lore = new ArrayList<>();
            for (String line : s.getStringList("lore")) {
                lore.add(core.messageService().renderInline(PapiBridge.apply(player, line), ph));
            }
            meta.setLore(lore);
            // Store the action on the item itself, so reordering the config list
            // never makes an old item do something else.
            String action = s.isString("menu") ? "menu:" + s.getString("menu")
                    : s.isString("command") ? "command:" + s.getString("command") : "";
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
            stack.setItemMeta(meta);
            player.getInventory().setItem(Math.max(0, Math.min(8, s.getInt("slot", i))), stack);
        }
    }

    /** Removes every lobby item from the player's inventory. */
    public void take(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isLobbyItem(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean isLobbyItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> give(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> give(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaLeave(ArenaPlayerLeaveEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> give(event.player()), 10L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArenaJoin(ArenaPlayerJoinEvent event) {
        take(event.player());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isLobbyItem(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!active()) {
            take(player); // the feature was turned off: clean up old items
            return;
        }
        String action = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action.startsWith("menu:")) {
            core.menus().open(player, action.substring(5), Map.of());
        } else if (action.startsWith("command:")) {
            String cmd = action.substring(8);
            player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (isLobbyItem(event.getCurrentItem()) || isLobbyItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isLobbyItem(event.getMainHandItem()) || isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
