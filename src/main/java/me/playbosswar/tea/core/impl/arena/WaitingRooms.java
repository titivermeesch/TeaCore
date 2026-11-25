package me.playbosswar.tea.core.impl.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.map.MapOrigin;
import me.playbosswar.tea.core.impl.map.SchematicStore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Each arena gets its own copy of the room, pasted {@link #OFFSET_X} blocks from the map
 * so players of different arenas never see each other. Lookup order: the map's room,
 * then the game's, then none.
 */
public final class WaitingRooms implements Listener {

    public static final int OFFSET_X = 2000;
    /** Extra blocks around a room that still count as "in the room". */
    private static final int MARGIN = 4;

    /** A saved room. {@code spawn} and {@code size} are relative to the room's min corner. */
    public record Room(String id, Path schematic, double[] spawn, float yaw, float pitch, int[] size) {
        Location origin(World world) {
            return new Location(world, MapOrigin.X + OFFSET_X, MapOrigin.Y, MapOrigin.Z);
        }

        public Location spawnIn(World world) {
            Location o = origin(world);
            return new Location(world, o.getX() + spawn[0], o.getY() + spawn[1], o.getZ() + spawn[2], yaw, pitch);
        }

        public boolean contains(Location at) {
            if (at.getWorld() == null) {
                return false;
            }
            Location o = origin(at.getWorld());
            return at.getX() >= o.getX() - MARGIN && at.getX() <= o.getX() + size[0] + MARGIN
                    && at.getY() >= o.getY() - MARGIN - 64 && at.getY() <= o.getY() + size[1] + MARGIN
                    && at.getZ() >= o.getZ() - MARGIN && at.getZ() <= o.getZ() + size[2] + MARGIN;
        }
    }

    private static final class Draft {
        final String roomId;
        final String worldName;
        final Location back;
        Location pos1;
        Location pos2;
        Location spawn;

        Draft(String roomId, String worldName, Location back) {
            this.roomId = roomId;
            this.worldName = worldName;
            this.back = back;
        }
    }

    private final Plugin plugin;
    private final Logger logger;
    private final Path dir;
    private final Path file;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, String> gameRooms = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> mapRooms = new LinkedHashMap<>();
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private ArenaManagerImpl arenas;

    public WaitingRooms(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.dir = plugin.getDataFolder().toPath().resolve("waiting-rooms");
        this.file = dir.resolve("waiting-rooms.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::showDrafts, 20L, 10L);
    }

    void attach(ArenaManagerImpl arenas) {
        this.arenas = arenas;
    }

    public synchronized void load() {
        rooms.clear();
        gameRooms.clear();
        mapRooms.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection rs = yaml.getConfigurationSection("rooms");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(id);
                if (r == null) {
                    continue;
                }
                Path schematic = nativeSchematic(r.getString("schematic", id + ".schem"));
                List<Integer> size = r.getIntegerList("size");
                rooms.put(id, new Room(id, schematic,
                        new double[] {r.getDouble("spawn.x"), r.getDouble("spawn.y"), r.getDouble("spawn.z")},
                        (float) r.getDouble("spawn.yaw"), (float) r.getDouble("spawn.pitch"),
                        new int[] {size.size() > 0 ? size.get(0) : 1, size.size() > 1 ? size.get(1) : 1,
                                size.size() > 2 ? size.get(2) : 1}));
            }
        }
        ConfigurationSection games = yaml.getConfigurationSection("games");
        if (games != null) {
            for (String game : games.getKeys(false)) {
                gameRooms.put(game, games.getString(game));
            }
        }
        ConfigurationSection maps = yaml.getConfigurationSection("maps");
        if (maps != null) {
            for (String game : maps.getKeys(false)) {
                ConfigurationSection m = maps.getConfigurationSection(game);
                if (m == null) {
                    continue;
                }
                Map<String, String> perMap = new LinkedHashMap<>();
                for (String map : m.getKeys(false)) {
                    perMap.put(map, m.getString(map));
                }
                mapRooms.put(game, perMap);
            }
        }
    }

    /** {@code room.schem} or {@code room.schematic}, whichever this server reads. */
    private Path nativeSchematic(String name) {
        Path given = dir.resolve(name);
        Path own = dir.resolve(name.replaceFirst("\\.[^.]+$", "") + "." + SchematicStore.current().extension());
        return Files.isRegularFile(own) ? own : given;
    }

    private synchronized void save() throws IOException {
        Files.createDirectories(dir);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("TeaCore waiting rooms. Edit in game with /tea waiting room ...");
        for (Room r : rooms.values()) {
            String p = "rooms." + r.id();
            yaml.set(p + ".schematic", r.schematic().getFileName().toString());
            yaml.set(p + ".spawn.x", r.spawn()[0]);
            yaml.set(p + ".spawn.y", r.spawn()[1]);
            yaml.set(p + ".spawn.z", r.spawn()[2]);
            yaml.set(p + ".spawn.yaw", (double) r.yaw());
            yaml.set(p + ".spawn.pitch", (double) r.pitch());
            yaml.set(p + ".size", List.of(r.size()[0], r.size()[1], r.size()[2]));
        }
        gameRooms.forEach((game, room) -> yaml.set("games." + game, room));
        mapRooms.forEach((game, perMap) -> perMap.forEach((map, room) -> yaml.set("maps." + game + "." + map, room)));
        yaml.save(file.toFile());
    }

    public synchronized List<Room> all() {
        return new ArrayList<>(rooms.values());
    }

    public synchronized Optional<Room> get(String id) {
        return Optional.ofNullable(rooms.get(id));
    }

    public synchronized Optional<Room> roomFor(GameId game, @Nullable String mapId) {
        String id = null;
        if (mapId != null) {
            id = mapRooms.getOrDefault(game.value(), Map.of()).get(mapId);
        }
        if (id == null) {
            id = gameRooms.get(game.value());
        }
        Room room = id == null ? null : rooms.get(id);
        if (id != null && room == null) {
            logger.warning("Waiting room '" + id + "' is assigned to " + game.value()
                    + (mapId == null ? "" : "/" + mapId) + " but doesn't exist.");
        }
        return Optional.ofNullable(room);
    }

    /** Assigns a room to a game (map null) or one map; room null removes the assignment. */
    public synchronized void assign(GameId game, @Nullable String mapId, @Nullable String roomId) throws IOException {
        if (mapId == null) {
            if (roomId == null) {
                gameRooms.remove(game.value());
            } else {
                gameRooms.put(game.value(), roomId);
            }
        } else {
            Map<String, String> perMap = mapRooms.computeIfAbsent(game.value(), k -> new LinkedHashMap<>());
            if (roomId == null) {
                perMap.remove(mapId);
            } else {
                perMap.put(mapId, roomId);
            }
        }
        save();
        me.playbosswar.tea.core.impl.map.MapChanges.changed();
    }

    public synchronized Map<String, String> gameAssignments() {
        return Map.copyOf(gameRooms);
    }

    public synchronized Map<String, Map<String, String>> mapAssignments() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        mapRooms.forEach((g, m) -> out.put(g, Map.copyOf(m)));
        return out;
    }

    public synchronized boolean delete(String id) throws IOException {
        Room r = rooms.remove(id);
        if (r == null) {
            return false;
        }
        gameRooms.values().removeIf(id::equals);
        mapRooms.values().forEach(m -> m.values().removeIf(id::equals));
        for (String ext : new String[] {"schem", "schematic"}) {
            Files.deleteIfExists(dir.resolve(id + "." + ext));
        }
        save();
        me.playbosswar.tea.core.impl.map.MapChanges.changed();
        return true;
    }

    /** Off the main thread only when {@link SchematicStore#pastesOffMainThread()}. */
    void paste(Room room, World world) {
        try {
            Location o = room.origin(world);
            SchematicStore.current().paste(room.schematic(), world, o.getBlockX(), o.getBlockY(), o.getBlockZ());
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not paste waiting room " + room.id() + " into " + world.getName(), e);
        }
    }

    private Optional<Room> roomOfArenaAt(Location at) {
        if (arenas == null || at.getWorld() == null) {
            return Optional.empty();
        }
        for (Arena arena : arenas.all()) {
            if (arena instanceof ArenaImpl impl && impl.waitingRoom() != null
                    && arena.world().map(w -> w.equals(at.getWorld())).orElse(false)
                    && impl.waitingRoom().contains(at)) {
                return Optional.of(impl.waitingRoom());
            }
        }
        return Optional.empty();
    }

    /** Rooms can't be broken, built on or blown up. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (roomOfArenaAt(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (roomOfArenaAt(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> roomOfArenaAt(b.getLocation()).isPresent());
    }

    public boolean editing(Player player) {
        return drafts.containsKey(player.getUniqueId());
    }

    public void edit(Player player, String roomId) throws IOException {
        if (drafts.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("You are already editing a waiting room. Save or discard it first.");
        }
        if (!roomId.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Room names use a-z, 0-9, _ and - (up to 32 characters).");
        }
        String worldName = "tea_edit_room_" + player.getUniqueId().toString().substring(0, 8) + "_" + roomId;
        WorldCreator creator = TeaCoreAPI.get().platform().worlds().voidWorld(worldName)
                .environment(World.Environment.NORMAL).generateStructures(false);
        World world = ArenaWorlds.create(creator);
        if (world == null) {
            throw new IllegalStateException("Could not create the editor world.");
        }
        world.setAutoSave(false);
        Draft draft = new Draft(roomId, worldName, player.getLocation().clone());
        Location origin = new Location(world, MapOrigin.X + 0.5, MapOrigin.Y, MapOrigin.Z + 0.5);
        Room existing = get(roomId).orElse(null);
        if (existing != null && Files.isRegularFile(existing.schematic())) {
            SchematicStore.current().paste(existing.schematic(), world, MapOrigin.X, MapOrigin.Y, MapOrigin.Z);
            draft.pos1 = new Location(world, MapOrigin.X, MapOrigin.Y, MapOrigin.Z);
            draft.pos2 = new Location(world, MapOrigin.X + existing.size()[0] - 1,
                    MapOrigin.Y + existing.size()[1] - 1, MapOrigin.Z + existing.size()[2] - 1);
            draft.spawn = new Location(world, MapOrigin.X + existing.spawn()[0], MapOrigin.Y + existing.spawn()[1],
                    MapOrigin.Z + existing.spawn()[2], existing.yaw(), existing.pitch());
            origin = draft.spawn.clone();
        } else {
            // Something to stand on in the void.
            Block floor = world.getBlockAt(MapOrigin.X, MapOrigin.Y - 1, MapOrigin.Z);
            floor.setType(TeaCoreAPI.get().platform().items().material("GLASS").orElse(Material.STONE));
        }
        drafts.put(player.getUniqueId(), draft);
        player.teleport(origin);
        player.setGameMode(GameMode.CREATIVE);
    }

    private Draft draftOf(Player player) {
        Draft d = drafts.get(player.getUniqueId());
        if (d == null) {
            throw new IllegalStateException("You aren't editing a waiting room. Start with /tea waiting room edit <name>.");
        }
        if (!player.getWorld().getName().equals(d.worldName)) {
            throw new IllegalStateException("Go back to the room's editor world first.");
        }
        return d;
    }

    public void setPos(Player player, int which) {
        Draft d = draftOf(player);
        Location at = player.getLocation().getBlock().getLocation();
        if (which == 1) {
            d.pos1 = at;
        } else {
            d.pos2 = at;
        }
    }

    public void setSpawn(Player player) {
        draftOf(player).spawn = player.getLocation().clone();
    }

    public List<String> missing(Player player) {
        Draft d = drafts.get(player.getUniqueId());
        List<String> out = new ArrayList<>();
        if (d == null) {
            return out;
        }
        if (d.pos1 == null) out.add("pos1");
        if (d.pos2 == null) out.add("pos2");
        if (d.spawn == null) out.add("spawn");
        return out;
    }

    public Room save(Player player) throws IOException {
        Draft d = draftOf(player);
        if (!missing(player).isEmpty()) {
            throw new IllegalStateException("Still missing: " + String.join(", ", missing(player)));
        }
        Files.createDirectories(dir);
        Path out = dir.resolve(d.roomId + "." + SchematicStore.current().extension());
        World world = player.getWorld();
        int[] min = SchematicStore.current().saveRegion(world,
                d.pos1.getBlockX(), d.pos1.getBlockY(), d.pos1.getBlockZ(),
                d.pos2.getBlockX(), d.pos2.getBlockY(), d.pos2.getBlockZ(), out);
        int[] size = {Math.abs(d.pos1.getBlockX() - d.pos2.getBlockX()) + 1,
                Math.abs(d.pos1.getBlockY() - d.pos2.getBlockY()) + 1,
                Math.abs(d.pos1.getBlockZ() - d.pos2.getBlockZ()) + 1};
        Room room = new Room(d.roomId, out,
                new double[] {d.spawn.getX() - min[0], d.spawn.getY() - min[1], d.spawn.getZ() - min[2]},
                d.spawn.getYaw(), d.spawn.getPitch(), size);
        synchronized (this) {
            rooms.put(room.id(), room);
        }
        save();
        leave(player, d);
        me.playbosswar.tea.core.impl.map.MapChanges.changed();
        return room;
    }

    public void discard(Player player) {
        Draft d = drafts.get(player.getUniqueId());
        if (d == null) {
            throw new IllegalStateException("You aren't editing a waiting room.");
        }
        leave(player, d);
    }

    private void leave(Player player, Draft d) {
        drafts.remove(player.getUniqueId());
        Location back = d.back;
        if (back == null || back.getWorld() == null || back.getWorld().getName().equals(d.worldName)) {
            back = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.teleport(back);
        player.setGameMode(GameMode.SURVIVAL);
        destroyWorld(d.worldName);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Draft d = drafts.remove(event.getPlayer().getUniqueId());
        if (d != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> destroyWorld(d.worldName), 20L);
        }
    }

    private void destroyWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            return;
        }
        for (Player p : world.getPlayers()) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        Path folder = world.getWorldFolder().toPath();
        if (!Bukkit.unloadWorld(world, false)) {
            logger.warning("Could not unload waiting room editor world " + name);
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not delete " + folder, e);
        }
    }

    private void showDrafts() {
        var particles = TeaCoreAPI.get().platform().particles();
        for (Map.Entry<UUID, Draft> e : drafts.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            Draft d = e.getValue();
            if (p == null || !p.getWorld().getName().equals(d.worldName)) {
                continue;
            }
            if (d.pos1 != null) {
                particles.dust(d.pos1.clone().add(0.5, 0.5, 0.5), Color.LIME, 1.5f, 6, 0.25, p);
            }
            if (d.pos2 != null) {
                particles.dust(d.pos2.clone().add(0.5, 0.5, 0.5), Color.AQUA, 1.5f, 6, 0.25, p);
            }
            if (d.spawn != null) {
                for (double y = 0; y < 2; y += 0.4) {
                    particles.dust(d.spawn.clone().add(0, y, 0), Color.YELLOW, 1.2f, 1, 0.02, p);
                }
            }
            if (d.pos1 != null && d.pos2 != null) {
                outline(p, d.pos1, d.pos2);
            }
        }
    }

    private void outline(Player p, Location a, Location b) {
        var particles = TeaCoreAPI.get().platform().particles();
        double x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        double z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        double step = Math.max(1, (x2 - x1 + z2 - z1 + y2 - y1) / 60);
        for (double x = x1; x <= x2; x += step) {
            for (double y : new double[] {y1, y2}) {
                for (double z : new double[] {z1, z2}) {
                    particles.dust(new Location(a.getWorld(), x, y, z), Color.WHITE, 0.8f, 1, 0, p);
                }
            }
        }
        for (double z = z1; z <= z2; z += step) {
            for (double y : new double[] {y1, y2}) {
                for (double x : new double[] {x1, x2}) {
                    particles.dust(new Location(a.getWorld(), x, y, z), Color.WHITE, 0.8f, 1, 0, p);
                }
            }
        }
        for (double y = y1; y <= y2; y += step) {
            for (double x : new double[] {x1, x2}) {
                for (double z : new double[] {z1, z2}) {
                    particles.dust(new Location(a.getWorld(), x, y, z), Color.WHITE, 0.8f, 1, 0, p);
                }
            }
        }
    }

    static boolean waiting(ArenaState state) {
        return state == ArenaState.WAITING || state == ArenaState.COUNTDOWN || state == ArenaState.ALLOCATING;
    }

    static String key(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
