package me.playbosswar.tea.core.impl.arena;

import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.feature.FeatureService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class WaitingLobby implements Listener {

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final FeatureService features;

    public WaitingLobby(Plugin plugin, TeaCoreAPI core, FeatureService features) {
        this.plugin = plugin;
        this.core = core;
        this.features = features;
        me.playbosswar.tea.core.impl.platform.LockedItems.register(this::isItem);
    }

    private static boolean isWaiting(ArenaState state) {
        return state == ArenaState.WAITING || state == ArenaState.COUNTDOWN || state == ArenaState.STARTING;
    }

    private Arena waitingArena(Player player) {
        Arena arena = core.arenas().arenaOf(player.getUniqueId()).orElse(null);
        if (arena == null || !isWaiting(arena.state()) || !arena.players().contains(player.getUniqueId())) {
            return null;
        }
        return arena;
    }

    private boolean isProtected(Player player) {
        Arena arena = waitingArena(player);
        return arena != null && features.enabled(arena.gameId(), "waiting-protection");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && isProtected(victim)) {
            event.setCancelled(true);
        }
    }

    /** A player waiting for their match can't hurt players elsewhere either. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Player attacker = core.platform().entities().playerBehind(event.getDamager());
        if (attacker != null && isProtected(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    private int hotbarSlot() {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt("kit-item.hotbar-slot", 0)));
    }

    private boolean wantsKitItem(Arena arena) {
        return features.enabled(arena.gameId(), "kit-item")
                && !core.kits().kitsFor(arena.gameId()).isEmpty();
    }

    private boolean wantsLeaveItem(Arena arena) {
        return features.enabled(arena.gameId(), "leave-item");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ArenaPlayerJoinEvent event) {
        if (event.spectator() || !isWaiting(event.arena().state())) {
            return;
        }
        Player player = event.player();
        // After the game's own join handling, which may reset the inventory.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Arena arena = waitingArena(player);
            if (!player.isOnline() || arena == null) {
                return;
            }
            if (wantsKitItem(arena)) {
                giveItem(player);
            }
            if (wantsLeaveItem(arena)) {
                giveLeaveItem(player);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        takeItem(event.player());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onState(ArenaStateChangeEvent event) {
        if (isWaiting(event.to())) {
            return;
        }
        for (var uuid : event.arena().players()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                takeItem(player);
            }
        }
    }

    private void giveLeaveItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (me.playbosswar.tea.core.impl.platform.TeaTags.marked(item, "leave_item")) {
                return;
            }
        }
        String locale = core.messageService().resolveLocale(player);
        ItemStack item = core.platform().items()
                .stackOr(plugin.getConfig().getString("leave-item.material", "RED_BED"), "BED", 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(core.messageService().renderLegacyNoPrefix(locale, "core.leave-item.name", Map.of()));
        meta.setLore(List.of(core.messageService().renderLegacyNoPrefix(locale, "core.leave-item.lore", Map.of())));
        item.setItemMeta(meta);
        int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("leave-item.hotbar-slot", 8)));
        player.getInventory().setItem(slot, me.playbosswar.tea.core.impl.platform.TeaTags.mark(item, "leave_item"));
    }

    private void giveItem(Player player) {
        if (hasItem(player)) {
            return;
        }
        String locale = core.messageService().resolveLocale(player);
        ItemStack item = core.platform().items()
                .stackOr(plugin.getConfig().getString("kit-item.material", "NETHER_STAR"), "NETHER_STAR", 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(core.messageService().renderLegacyNoPrefix(locale, "core.kit-item.name", Map.of()));
        meta.setLore(List.of(core.messageService().renderLegacyNoPrefix(locale, "core.kit-item.lore", Map.of())));
        item.setItemMeta(meta);
        player.getInventory().setItem(hotbarSlot(), me.playbosswar.tea.core.impl.platform.TeaTags.mark(item, "kit_item"));
    }

    private boolean isItem(ItemStack item) {
        return me.playbosswar.tea.core.impl.platform.TeaTags.marked(item, "kit_item")
                || me.playbosswar.tea.core.impl.platform.TeaTags.marked(item, "leave_item");
    }

    private boolean hasItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (me.playbosswar.tea.core.impl.platform.TeaTags.marked(item, "kit_item")) {
                return true;
            }
        }
        return false;
    }

    private void takeItem(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isItem(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    /** LOWEST and not ignoring cancelled: right-clicking air is pre-cancelled for some items. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (!core.platform().players().isMainHand(event) || event.getAction() == Action.PHYSICAL
                || !isItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        Arena arena = waitingArena(event.getPlayer());
        if (arena == null) {
            takeItem(event.getPlayer());
            return;
        }
        if (me.playbosswar.tea.core.impl.platform.TeaTags.marked(event.getItem(), "leave_item")) {
            Bukkit.dispatchCommand(event.getPlayer(), "leave");
            return;
        }
        Bukkit.dispatchCommand(event.getPlayer(), "kit " + arena.gameId().value());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (isItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (isItem(event.getCurrentItem()) || isItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }
}
