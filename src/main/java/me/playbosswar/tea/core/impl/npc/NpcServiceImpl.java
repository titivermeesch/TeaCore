package me.playbosswar.tea.core.impl.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.map.MapExtensionKey;
import me.playbosswar.tea.core.api.map.MultiPointExtension;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import me.playbosswar.tea.core.api.npc.NpcDefinition;
import me.playbosswar.tea.core.api.npc.NpcService;
import me.playbosswar.tea.core.impl.map.MapOrigin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Config ({@code npcs.<game>.<id>}) overrides registered NPCs and can add NPCs no game registered. */
public final class NpcServiceImpl implements NpcService, Listener {

    private static final String KEY_PREFIX = "tea:npc:";

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final Logger logger;
    /** Key: "game|id". */
    private final Map<String, NpcDefinition> registered = new ConcurrentHashMap<>();
    private final Map<ArenaId, Map<Entity, Spawned>> spawned = new HashMap<>();
    private BukkitTask anchorTask;

    private record Spawned(NpcDefinition def, Location spot) { }

    public NpcServiceImpl(Plugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        this.logger = plugin.getLogger();
    }

    /** Registers map point commands for config-only NPCs. Call once at enable. */
    public void loadConfigNpcs() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("npcs");
        if (root == null) {
            return;
        }
        for (String game : root.getKeys(false)) {
            ConfigurationSection g = root.getConfigurationSection(game);
            if (g == null) continue;
            for (String id : g.getKeys(false)) {
                registerPoints(id);
            }
        }
    }

    @Override
    public void register(NpcDefinition definition) {
        registered.put(definition.game().value() + "|" + definition.id(), definition);
        registerPoints(definition.id());
    }

    @Override
    public Collection<NpcDefinition> npcsFor(GameId game) {
        Map<String, NpcDefinition> out = new LinkedHashMap<>();
        for (NpcDefinition def : registered.values()) {
            if (def.game().equals(game)) {
                out.put(def.id(), def);
            }
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("npcs." + game.value());
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) continue;
                NpcDefinition base = out.get(id);
                if (!c.getBoolean("enabled", true)) {
                    out.remove(id);
                    continue;
                }
                out.put(id, applyConfig(game, id, base, c));
            }
        }
        return out.values();
    }

    @Override
    public MapExtensionKey<List<SpawnPoint>> pointsKey(String id) {
        return MultiPointExtension.key(KEY_PREFIX + id.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isNpc(Entity entity) {
        return me.playbosswar.tea.core.impl.platform.TeaTags.marked(entity, "npc");
    }

    private void registerPoints(String id) {
        String sub = id.toLowerCase(Locale.ROOT);
        try {
            core.maps().extensions().register(MultiPointExtension.builder(pointsKey(sub))
                    .subcommand(sub).label(sub + " npc")
                    .icon(core.platform().items().material("VILLAGER_SPAWN_EGG").orElse(Material.EMERALD))
                    .markerColor(Color.fromRGB(0x20C040)).build());
        } catch (IllegalArgumentException reserved) {
            logger.warning("NPC id '" + id + "' clashes with a /tea map command; pick another id.");
        }
    }

    private NpcDefinition applyConfig(GameId game, String id, NpcDefinition base, ConfigurationSection c) {
        String menu = c.getString("menu", base == null ? null : base.menuId());
        String nameKey = base == null ? game.value() + ".npc." + id : base.nameKey();
        EntityType type = base == null ? EntityType.VILLAGER : base.entityType();
        String entity = c.getString("entity");
        if (entity != null) {
            try {
                EntityType parsed = EntityType.valueOf(entity.toUpperCase(Locale.ROOT));
                if (parsed.isAlive() && parsed.isSpawnable()) type = parsed;
                else logger.warning("npcs." + game.value() + "." + id + ".entity: " + entity + " is not a spawnable living entity");
            } catch (IllegalArgumentException e) {
                logger.warning("npcs." + game.value() + "." + id + ".entity: unknown entity " + entity);
            }
        }
        Set<ArenaState> states = base == null ? EnumSet.of(ArenaState.LIVE) : base.activeStates();
        List<String> phases = c.getStringList("phases");
        if (!phases.isEmpty()) {
            EnumSet<ArenaState> parsed = EnumSet.noneOf(ArenaState.class);
            for (String p : phases) {
                try {
                    parsed.add(ArenaState.valueOf(p.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    logger.warning("npcs." + game.value() + "." + id + ".phases: unknown state " + p);
                }
            }
            if (!parsed.isEmpty()) states = parsed;
        }
        return new NpcDefinition(game, id, menu, base == null ? null : base.onClick(), nameKey, type, states,
                base == null ? null : base.defaultSpots());
    }

    /** Config {@code name} (MiniMessage) wins over the definition's key. */
    private String displayName(NpcDefinition def) {
        String literal = plugin.getConfig().getString("npcs." + def.game().value() + "." + def.id() + ".name");
        if (literal != null && !literal.isBlank()) {
            return core.messageService().renderInline(literal, Map.of());
        }
        return core.messageService().renderLegacyNoPrefix(
                plugin.getConfig().getString("i18n.default-locale", "en_US"), def.nameKey(), Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaState(ArenaStateChangeEvent event) {
        Arena arena = event.arena();
        Collection<NpcDefinition> defs = npcsFor(arena.gameId());
        if (defs.isEmpty() && !spawned.containsKey(arena.id())) {
            return;
        }
        Map<Entity, Spawned> current = spawned.getOrDefault(arena.id(), Map.of());
        for (var entry : List.copyOf(current.entrySet())) {
            if (!entry.getValue().def().activeStates().contains(event.to())) {
                if (entry.getKey().isValid()) entry.getKey().remove();
                current.remove(entry.getKey());
            }
        }
        if (event.to() == ArenaState.RESETTING || event.to() == ArenaState.RELEASED) {
            despawnAll(arena.id());
            return;
        }
        for (NpcDefinition def : defs) {
            boolean present = current.values().stream().anyMatch(s -> s.def().id().equals(def.id()));
            if (def.activeStates().contains(event.to()) && !present) {
                spawn(arena, def);
            }
        }
    }

    private void spawn(Arena arena, NpcDefinition def) {
        World world = arena.world().orElse(null);
        MapDefinition map = arena.mapId().flatMap(id -> core.maps().get(arena.gameId(), id)).orElse(null);
        if (world == null || map == null) {
            return;
        }
        Location origin = MapOrigin.in(world);
        List<Location> spots = new ArrayList<>();
        for (SpawnPoint sp : map.extension(pointsKey(def.id())).orElse(List.of())) {
            spots.add(sp.toLocation(origin));
        }
        if (spots.isEmpty() && def.defaultSpots() != null) {
            spots.addAll(def.defaultSpots().apply(arena));
        }
        if (spots.isEmpty()) {
            logger.info("No spots for NPC '" + def.id() + "' in arena " + arena.id()
                    + " (place some with /tea map " + def.id() + ")");
            return;
        }
        String name = displayName(def);
        Map<Entity, Spawned> mine = spawned.computeIfAbsent(arena.id(), k -> new HashMap<>());
        for (Location spot : spots) {
            Entity entity = world.spawnEntity(spot, def.entityType());
            var control = core.platform().entities();
            control.setInvulnerable(entity, true);
            control.setSilent(entity, true);
            control.setGravity(entity, false);
            entity.setCustomName(name);
            entity.setCustomNameVisible(true);
            me.playbosswar.tea.core.impl.platform.TeaTags.set(entity, "npc", def.game().value() + "|" + def.id());
            if (entity instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                control.setCollidable(living, false);
                control.setAi(living, false);
            }
            mine.put(entity, new Spawned(def, spot.clone()));
        }
        logger.info("Spawned " + spots.size() + " '" + def.id() + "' NPC(s) in arena " + arena.id());
        if (anchorTask == null) {
            anchorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::anchor, 10L, 10L);
        }
    }

    private void despawnAll(ArenaId id) {
        Map<Entity, Spawned> mine = spawned.remove(id);
        if (mine != null) {
            mine.keySet().forEach(e -> { if (e.isValid()) e.remove(); });
        }
        if (spawned.isEmpty() && anchorTask != null) {
            anchorTask.cancel();
            anchorTask = null;
        }
    }

    /** Puts NPCs back if a blast or a push moved them. */
    private void anchor() {
        for (Map<Entity, Spawned> mine : spawned.values()) {
            for (var entry : mine.entrySet()) {
                Entity e = entry.getKey();
                Location spot = entry.getValue().spot();
                if (e.isValid() && e.getLocation().distanceSquared(spot) > 0.25) {
                    e.setVelocity(new Vector());
                    e.teleport(spot);
                }
            }
        }
    }

    public void shutdown() {
        for (ArenaId id : List.copyOf(spawned.keySet())) {
            despawnAll(id);
        }
    }

    private Spawned spawnedFor(Entity entity) {
        for (Map<Entity, Spawned> mine : spawned.values()) {
            Spawned s = mine.get(entity);
            if (s != null) return s;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true); // no trade screen, no leash, no name tag
        if (!core.platform().players().isMainHand(event)) {
            return;
        }
        Spawned s = spawnedFor(event.getRightClicked());
        if (s == null) {
            return;
        }
        Player player = event.getPlayer();
        NpcDefinition def = s.def();
        boolean opened = def.menuId() != null && core.menus().open(player, def.menuId(),
                Map.of("game", def.game().value(), "npc", def.id()));
        if (!opened && def.onClick() != null) {
            def.onClick().accept(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (isNpc(event.getEntity()) || (event.getTarget() != null && isNpc(event.getTarget()))) {
            event.setCancelled(true);
        }
    }
}
