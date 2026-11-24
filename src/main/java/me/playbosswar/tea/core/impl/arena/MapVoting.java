package me.playbosswar.tea.core.impl.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.feature.FeatureService;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.MapSelectionPolicy;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/** Ties are broken at random, no votes keeps the loaded map, and arenas with a requested map don't vote. */
public final class MapVoting implements Listener {

    private static final class Vote {
        final List<String> options;
        final Map<UUID, String> votes = new ConcurrentHashMap<>();

        Vote(List<String> options) {
            this.options = options;
        }

        Map<String, Integer> tally() {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (String o : options) {
                out.put(o, 0);
            }
            for (String v : votes.values()) {
                out.merge(v, 1, Integer::sum);
            }
            return out;
        }
    }

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final ArenaManagerImpl arenas;
    private final FeatureService features;
    private final Map<ArenaId, Vote> votes = new ConcurrentHashMap<>();
    /** Arenas switching maps right now (the countdown waits for them). */
    private final Set<ArenaId> switching = ConcurrentHashMap.newKeySet();

    public MapVoting(Plugin plugin, TeaCoreAPI core, ArenaManagerImpl arenas, FeatureService features) {
        this.plugin = plugin;
        this.core = core;
        this.arenas = arenas;
        this.features = features;
        me.playbosswar.tea.core.impl.platform.LockedItems.register(this::isItem);
    }

    public void register(MenuService menus) {
        menus.registerSource("map-vote", (viewer, args) -> entries(viewer));
        menus.registerAction("map-vote", (click, arg) -> {
            vote(click.player(), arg.trim());
            click.refresh();
        });
    }

    private int optionCount() {
        return Math.max(2, plugin.getConfig().getInt("map-voting.options", 3));
    }

    private int hotbarSlot() {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt("map-voting.hotbar-slot", 7)));
    }

    public java.util.Optional<Arena> votingArena(Player player) {
        return core.arenas().arenaOf(player.getUniqueId())
                .filter(a -> votes.containsKey(a.id()) && a.players().contains(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onState(ArenaStateChangeEvent event) {
        Arena arena = event.arena();
        if (event.to() == ArenaState.WAITING && event.from() == ArenaState.ALLOCATING) {
            open(arena);
        } else if (event.to() == ArenaState.RELEASED || event.to() == ArenaState.RESETTING) {
            votes.remove(arena.id());
            switching.remove(arena.id());
        }
    }

    private void open(Arena arena) {
        ArenaImpl impl = arenas.arenaImpl(arena.id());
        GameDefinition def = core.games().get(arena.gameId()).orElse(null);
        if (impl == null || def == null || impl.mapLocked() || arena.mapId().isEmpty()) {
            return;
        }
        Hosting.Mode mode = arenas.hosting() == null ? Hosting.Mode.DEFAULT
                : arenas.hosting().mapChoice(arena.gameId()).mode();
        if (mode == Hosting.Mode.RANDOM || mode == Hosting.Mode.FIXED) {
            return;
        }
        if (mode == Hosting.Mode.DEFAULT && (def.mapSelection() instanceof MapSelectionPolicy.Fixed
                || !features.enabled(arena.gameId(), "map-voting"))) {
            return;
        }
        List<String> others = new ArrayList<>();
        for (MapDefinition m : core.maps().forGame(arena.gameId())) {
            if (!m.mapId().equals(arena.mapId().get())) {
                others.add(m.mapId());
            }
        }
        if (others.isEmpty()) {
            return;
        }
        Collections.shuffle(others);
        List<String> options = new ArrayList<>();
        options.add(arena.mapId().get());
        options.addAll(others.subList(0, Math.min(others.size(), optionCount() - 1)));
        Collections.shuffle(options);
        votes.put(arena.id(), new Vote(List.copyOf(options)));
    }

    public void lock(ArenaId id) {
        ArenaImpl impl = arenas.arenaImpl(id);
        if (impl != null) {
            impl.lockMap();
        }
        Vote vote = votes.remove(id);
        if (vote != null && impl != null) {
            for (UUID uuid : impl.players()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    takeItem(p);
                }
            }
        }
    }

    /** Called when the countdown ends. Returns true while the arena must wait for a map switch. */
    public boolean holdBeforeStart(ArenaImpl arena) {
        if (switching.contains(arena.id())) {
            return true;
        }
        Vote vote = votes.remove(arena.id());
        if (vote == null) {
            return false;
        }
        for (UUID uuid : arena.players()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                takeItem(p);
            }
        }
        Map<String, Integer> tally = vote.tally();
        int best = tally.values().stream().max(Integer::compare).orElse(0);
        String current = arena.mapId().orElse(null);
        String winner = current;
        if (best > 0) {
            List<String> top = tally.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
            winner = top.get(ThreadLocalRandom.current().nextInt(top.size()));
        }
        // A map too small for everyone who joined can't win.
        MapDefinition winnerDef = winner == null ? null : core.maps().get(arena.gameId(), winner).orElse(null);
        if (winnerDef == null || winnerDef.maxPlayers() < arena.players().size()) {
            winner = current;
        }
        String name = displayName(arena, winner);
        for (UUID uuid : arena.allOccupants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                core.messageService().send(p, "core.vote.result", Map.of("map", name,
                        "votes", String.valueOf(best)));
            }
        }
        if (winner == null || winner.equals(current)) {
            return false;
        }
        switching.add(arena.id());
        arenas.switchMap(arena.id(), winner, () -> switching.remove(arena.id()));
        return switching.contains(arena.id());
    }

    private String displayName(Arena arena, String mapId) {
        return mapId == null ? "-" : core.maps().get(arena.gameId(), mapId)
                .map(MapDefinition::resolvedDisplayName).orElse(mapId);
    }

    public boolean vote(Player player, String mapId) {
        Arena arena = votingArena(player).orElse(null);
        Vote vote = arena == null ? null : votes.get(arena.id());
        if (vote == null) {
            core.messageService().send(player, "core.vote.none");
            return false;
        }
        String match = vote.options.stream().filter(o -> o.equalsIgnoreCase(mapId)).findFirst().orElse(null);
        if (match == null) {
            core.messageService().send(player, "core.vote.unknown", Map.of("map", mapId));
            return false;
        }
        vote.votes.put(player.getUniqueId(), match);
        int count = vote.tally().get(match);
        core.messageService().send(player, "core.vote.voted", Map.of("map", displayName(arena, match),
                "votes", String.valueOf(count)));
        return true;
    }

    private List<MenuEntry> entries(Player viewer) {
        Arena arena = votingArena(viewer).orElse(null);
        Vote vote = arena == null ? null : votes.get(arena.id());
        if (vote == null) {
            return List.of();
        }
        Map<String, Integer> tally = vote.tally();
        String mine = vote.votes.get(viewer.getUniqueId());
        List<MenuEntry> out = new ArrayList<>();
        for (String option : vote.options) {
            MapDefinition m = core.maps().get(arena.gameId(), option).orElse(null);
            Map<String, String> v = new HashMap<>();
            v.put("map_id", option);
            v.put("map_name", displayName(arena, option));
            v.put("votes", String.valueOf(tally.getOrDefault(option, 0)));
            v.put("voted", String.valueOf(option.equals(mine)));
            v.put("min", m == null ? "-" : String.valueOf(m.minPlayers()));
            v.put("max", m == null ? "-" : String.valueOf(m.maxPlayers()));
            out.add(new MenuEntry(v, null));
        }
        return out;
    }

    public String summary(Player player) {
        Arena arena = votingArena(player).orElse(null);
        Vote vote = arena == null ? null : votes.get(arena.id());
        if (vote == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        vote.tally().forEach((map, n) -> parts.add(map + " (" + n + ")"));
        return String.join(", ", parts);
    }

    public List<String> options(Player player) {
        Arena arena = votingArena(player).orElse(null);
        Vote vote = arena == null ? null : votes.get(arena.id());
        return vote == null ? List.of() : vote.options;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ArenaPlayerJoinEvent event) {
        if (event.spectator() || !votes.containsKey(event.arena().id())) {
            return;
        }
        Player player = event.player();
        // After the game's own join handling (which may reset the inventory).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (votingArena(player).isPresent()) {
                giveItem(player);
                core.messageService().send(player, "core.vote.prompt", Map.of("maps", summary(player)));
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        Vote vote = votes.get(event.arena().id());
        if (vote != null) {
            vote.votes.remove(event.player().getUniqueId());
        }
        takeItem(event.player());
    }

    private void giveItem(Player player) {
        String locale = core.messageService().resolveLocale(player);
        ItemStack item = core.platform().items().stackOr("FILLED_MAP", "PAPER", 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(core.messageService().renderLegacyNoPrefix(locale, "core.vote.item.name", Map.of()));
        meta.setLore(List.of(core.messageService().renderLegacyNoPrefix(locale, "core.vote.item.lore", Map.of())));
        item.setItemMeta(meta);
        player.getInventory().setItem(hotbarSlot(), me.playbosswar.tea.core.impl.platform.TeaTags.mark(item, "map_vote"));
    }

    private boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && me.playbosswar.tea.core.impl.platform.TeaTags.marked(item, "map_vote");
    }

    private void takeItem(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isItem(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (!core.platform().players().isMainHand(event) || event.getAction() == Action.PHYSICAL
                || !isItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        core.menus().open(event.getPlayer(), "map-vote", Map.of());
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
