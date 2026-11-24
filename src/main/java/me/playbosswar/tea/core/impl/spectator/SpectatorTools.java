package me.playbosswar.tea.core.impl.spectator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.combat.MatchStats;
import me.playbosswar.tea.core.api.feature.FeatureService;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class SpectatorTools implements Listener {

    private static final String TOOL_TELEPORTER = "teleporter";
    private static final String TOOL_AGAIN = "again";
    private static final String TOOL_LEAVE = "leave";

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final FeatureService features;
    private final Map<UUID, ArenaId> ghosts = new ConcurrentHashMap<>();
    /** spectator -> followed player. */
    private final Map<UUID, UUID> following = new ConcurrentHashMap<>();
    private BukkitTask task;

    public SpectatorTools(Plugin plugin, TeaCoreAPI core, FeatureService features) {
        this.plugin = plugin;
        this.core = core;
        this.features = features;
        me.playbosswar.tea.core.impl.platform.LockedItems.register(i -> toolOf(i) != null);
        me.playbosswar.tea.core.impl.platform.Ghosts.register(this::ghost);
    }

    public void start(MenuService menus) {
        menus.registerSource("spectate-targets", (viewer, args) -> targets(viewer));
        menus.registerAction("spectate-tp", (click, arg) -> teleportTo(click.player(), arg.trim()));
        menus.registerAction("spectate-follow", (click, arg) -> follow(click.player(), arg.trim()));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID uuid : List.copyOf(ghosts.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                unghost(p, false);
            }
        }
    }

    /** True for ghosts and for spectators following someone. */
    public boolean isGhost(UUID uuid) {
        return ghosts.containsKey(uuid);
    }

    public Optional<UUID> watching(UUID spectator) {
        return Optional.ofNullable(following.get(spectator));
    }

    private boolean enabledFor(Arena arena) {
        return features.enabled(arena.gameId(), "spectator-tools");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ArenaPlayerJoinEvent event) {
        if (event.spectator()) {
            Player player = event.player();
            // After TeaCore has set the game's spectator game mode.
            Bukkit.getScheduler().runTask(plugin, () -> sweepOne(player));
        } else {
            hideGhostsFrom(event.player());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        if (ghosts.containsKey(event.player().getUniqueId())) {
            unghost(event.player(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (ghosts.containsKey(uuid)) {
            unghost(event.getPlayer(), true);
        }
        following.values().removeIf(uuid::equals);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!ghosts.containsKey(event.getPlayer().getUniqueId())) {
            hideGhostsFrom(event.getPlayer());
        }
    }

    /** Polls arena membership so every way of becoming a spectator is covered. */
    private void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sweepOne(player);
        }
    }

    private void sweepOne(Player player) {
        UUID uuid = player.getUniqueId();
        Arena arena = core.arenas().arenaOf(uuid).orElse(null);
        boolean spectator = arena != null && arena.spectators().contains(uuid) && enabledFor(arena);
        if (!spectator) {
            if (ghosts.containsKey(uuid)) {
                unghost(player, true);
            }
            return;
        }
        UUID target = following.get(uuid);
        if (target != null) {
            Player t = Bukkit.getPlayer(target);
            if (t == null || !arena.players().contains(target) || !t.getWorld().equals(player.getWorld())) {
                stopFollowing(player);
            }
            return;
        }
        if (!arena.id().equals(ghosts.get(uuid)) || player.getGameMode() != GameMode.ADVENTURE) {
            ghost(player, arena);
        }
    }

    private void ghost(Player player, Arena arena) {
        ghosts.put(player.getUniqueId(), arena.id());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        core.platform().entities().setInvulnerable(player, true);
        core.platform().entities().setCollidable(player, false);
        player.setCanPickupItems(false);
        player.setFireTicks(0);
        player.setFoodLevel(20);
        @SuppressWarnings("deprecation") // the attribute's name changed across versions
        double maxHealth = player.getMaxHealth();
        player.setHealth(maxHealth);
        player.getInventory().clear();
        player.getInventory().setItem(0, tool(player, TOOL_TELEPORTER, Material.COMPASS));
        player.getInventory().setItem(4, tool(player, TOOL_AGAIN, Material.PAPER));
        player.getInventory().setItem(8, tool(player, TOOL_LEAVE, core.platform().items().material("RED_BED").orElse(Material.BARRIER)));
        player.getInventory().setHeldItemSlot(0);
        for (Player other : player.getWorld().getPlayers()) {
            if (!ghosts.containsKey(other.getUniqueId())) {
                core.platform().players().setVisible(other, player, false);
            }
        }
    }

    private void unghost(Player player, boolean clearItems) {
        ghosts.remove(player.getUniqueId());
        following.remove(player.getUniqueId());
        core.platform().entities().setInvulnerable(player, false);
        core.platform().entities().setCollidable(player, true);
        player.setCanPickupItems(true);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (clearItems) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                if (toolOf(player.getInventory().getItem(slot)) != null) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            core.platform().players().setVisible(other, player, true);
        }
    }

    private void hideGhostsFrom(Player viewer) {
        for (UUID ghost : ghosts.keySet()) {
            Player g = Bukkit.getPlayer(ghost);
            if (g != null && !g.equals(viewer)) {
                core.platform().players().setVisible(viewer, g, false);
            }
        }
    }

    private ItemStack tool(Player player, String id, Material material) {
        String locale = core.messageService().resolveLocale(player);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(core.messageService().renderLegacyNoPrefix(locale, "core.spectator.item." + id, Map.of()));
        meta.setLore(List.of(core.messageService().renderLegacyNoPrefix(locale,
                "core.spectator.item." + id + "-lore", Map.of())));
        item.setItemMeta(meta);
        return me.playbosswar.tea.core.impl.platform.TeaTags.set(item, "spectator_tool", id);
    }

    private String toolOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return me.playbosswar.tea.core.impl.platform.TeaTags.get(item, "spectator_tool");
    }

    private void use(Player player, String tool) {
        Arena arena = core.arenas().arenaOf(player.getUniqueId()).orElse(null);
        if (arena == null) {
            return;
        }
        switch (tool) {
            case TOOL_TELEPORTER -> core.menus().open(player, "spectate", Map.of());
            case TOOL_AGAIN -> {
                String game = arena.gameId().value();
                player.performCommand("play " + game);
            }
            case TOOL_LEAVE -> player.performCommand("leave");
            default -> { }
        }
    }

    private List<MenuEntry> targets(Player viewer) {
        Arena arena = core.arenas().arenaOf(viewer.getUniqueId()).orElse(null);
        if (arena == null) {
            return List.of();
        }
        String locale = core.messageService().resolveLocale(viewer);
        List<Player> players = new ArrayList<>();
        for (UUID uuid : arena.players()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                players.add(p);
            }
        }
        players.sort(Comparator.comparing((Player p) -> core.teams().teamOf(arena.id(), p.getUniqueId())
                .map(t -> t.id().value()).orElse("")).thenComparing(Player::getName));
        List<MenuEntry> out = new ArrayList<>();
        for (Player p : players) {
            MatchStats stats = core.combat().matchStats(arena.id(), p.getUniqueId());
            Optional<Team> team = core.teams().teamOf(arena.id(), p.getUniqueId());
            Map<String, String> v = new HashMap<>();
            v.put("uuid", p.getUniqueId().toString());
            v.put("name", p.getName());
            v.put("team", team.map(t -> core.messageService().renderPlainText(locale, t.displayNameKey(), Map.of()))
                    .orElse("-"));
            v.put("health", String.valueOf((int) Math.ceil(p.getHealth())));
            v.put("kills", String.valueOf(stats.kills()));
            v.put("deaths", String.valueOf(stats.deaths()));
            v.put("assists", String.valueOf(stats.assists()));
            ItemStack head = core.platform().items().stackOr("PLAYER_HEAD", "PAPER", 1);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                if (Bukkit.getOnlineMode()) {
                    skull.setOwner(p.getName());
                }
                head.setItemMeta(skull);
            }
            out.add(new MenuEntry(v, head));
        }
        return out;
    }

    private Player target(Player spectator, String rawUuid) {
        Arena arena = core.arenas().arenaOf(spectator.getUniqueId()).orElse(null);
        if (arena == null || !arena.spectators().contains(spectator.getUniqueId())) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(rawUuid);
            Player t = Bukkit.getPlayer(uuid);
            if (t == null || !arena.players().contains(uuid)) {
                core.messageService().send(spectator, "core.spectator.gone");
                return null;
            }
            return t;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void teleportTo(Player spectator, String rawUuid) {
        Player t = target(spectator, rawUuid);
        if (t == null) {
            return;
        }
        spectator.closeInventory();
        if (following.containsKey(spectator.getUniqueId())) {
            stopFollowing(spectator);
        }
        Location at = t.getLocation().clone().add(0, 1.5, 0);
        spectator.teleport(at);
        spectator.setFlying(true);
        core.messageService().send(spectator, "core.spectator.teleported", Map.of("player", t.getName()));
    }

    private void follow(Player spectator, String rawUuid) {
        Player t = target(spectator, rawUuid);
        if (t == null) {
            return;
        }
        spectator.closeInventory();
        following.put(spectator.getUniqueId(), t.getUniqueId());
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.teleport(t.getLocation());
        // The camera needs the target loaded on the client: set it a tick later.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (t.getUniqueId().equals(following.get(spectator.getUniqueId())) && t.isOnline()) {
                spectator.setSpectatorTarget(t);
            }
        }, 2L);
        core.messageService().send(spectator, "core.spectator.following", Map.of("player", t.getName()));
    }

    private void stopFollowing(Player spectator) {
        following.remove(spectator.getUniqueId());
        if (spectator.getGameMode() == GameMode.SPECTATOR) {
            spectator.setSpectatorTarget(null);
        }
        Arena arena = core.arenas().arenaOf(spectator.getUniqueId()).orElse(null);
        if (arena != null && arena.spectators().contains(spectator.getUniqueId())) {
            ghost(spectator, arena);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && following.containsKey(event.getPlayer().getUniqueId())) {
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> stopFollowing(p));
        }
    }

    private boolean ghost(Entity entity) {
        return entity instanceof Player p && ghosts.containsKey(p.getUniqueId())
                && !following.containsKey(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!ghost(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        String tool = toolOf(event.getItem());
        if (tool != null && event.getAction() != org.bukkit.event.block.Action.PHYSICAL
                && core.platform().players().isMainHand(event)) {
            use(event.getPlayer(), tool);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfGhost(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (ghost(damager) || ghost(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        cancelIfGhost(event.getEntity(), event);
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && ghost(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation") // the only pickup event on every version
    public void onPickup(PlayerPickupItemEvent event) {
        cancelIfGhost(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfGhost(event.getPlayer(), event);
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (ghost(event.getWhoClicked()) && event.getClickedInventory() == event.getWhoClicked().getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent event) {
        cancelIfGhost(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        cancelIfGhost(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        cancelIfGhost(event.getPlayer(), event);
    }

    /** A ghost standing in the way doesn't stop players from placing blocks. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCanBuild(BlockCanBuildEvent event) {
        if (event.isBuildable() || ghosts.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        var inside = block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5);
        if (!inside.isEmpty() && inside.stream().allMatch(this::ghost)) {
            event.setBuildable(true);
        }
    }

    private void cancelIfGhost(Entity entity, Cancellable event) {
        if (ghost(entity)) {
            event.setCancelled(true);
        }
    }

    public Set<UUID> ghosts() {
        return Set.copyOf(ghosts.keySet());
    }
}
