package me.playbosswar.tea.core.impl.map;

import com.sk89q.worldedit.math.BlockVector3;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.map.MapExtension;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages per-player map-authoring sessions. Each session runs in its own
 * temporary
 * void world {@code tea_edit_<playerShortUuid>_<game>_<map>}. On create/edit we
 * spin the
 * world up; on save/discard we unload it, delete its folder, and teleport the
 * player back.
 */
public final class MapEditingService {

    private final Plugin plugin;
    private final MapService mapService;
    private final MapPersistence persistence;
    private final MapExtensionRegistryImpl extensionRegistry;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, MapDraft> drafts = new ConcurrentHashMap<>();

    public MapEditingService(Plugin plugin, MapService mapService, MapPersistence persistence,
            MapExtensionRegistryImpl extensionRegistry, Logger logger) {
        this.plugin = plugin;
        this.mapService = mapService;
        this.persistence = persistence;
        this.extensionRegistry = extensionRegistry;
        this.logger = logger;
    }

    public void loadAllAndRegister() {
        for (MapDefinition def : persistence.loadAll()) {
            mapService.register(def);
        }
    }

    public Optional<MapDraft> currentDraft(Player player) {
        return Optional.ofNullable(drafts.get(player.getUniqueId()));
    }

    /**
     * Create a brand-new map. Fails if a map with this id already exists or if the
     * player is already editing.
     */
    public MapDraft create(Player player, GameId gameId, String mapId) {
        if (drafts.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("You already have an active draft. Save or discard it first.");
        }
        if (mapService.get(gameId, mapId).isPresent()) {
            throw new IllegalStateException(
                    "Map " + gameId.value() + "/" + mapId + " already exists; use /tea map edit instead.");
        }
        String worldName = editWorldName(player, gameId, mapId);
        Location pre = player.getLocation().clone();
        World world = createEditWorld(worldName);
        MapDraft draft = new MapDraft(player.getUniqueId(), gameId, mapId, worldName, pre);
        drafts.put(player.getUniqueId(), draft);
        player.teleport(MapOrigin.in(world));
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        return draft;
    }

    /**
     * Edit an existing map: spin up a void world and paste the saved schematic into
     * it.
     */
    public MapDraft edit(Player player, GameId gameId, String mapId) throws IOException {
        if (drafts.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("You already have an active draft. Save or discard it first.");
        }
        MapDefinition def = mapService.get(gameId, mapId)
                .orElseThrow(() -> new IllegalStateException(
                        "Map " + gameId.value() + "/" + mapId + " does not exist; use /tea map create."));

        String worldName = editWorldName(player, gameId, mapId);
        Location pre = player.getLocation().clone();
        World world = createEditWorld(worldName);

        if (def.schematic().isPresent() && Files.isRegularFile(def.schematicFile())) {
            try {
                WorldEditPaster.paste(def.schematicFile(), world, MapOrigin.X, MapOrigin.Y, MapOrigin.Z);

                BlockVector3 dims = WorldEditPaster.readDimensions(def.schematicFile());
                MapDraft draft = new MapDraft(player.getUniqueId(), gameId, mapId, worldName, pre);
                Location origin = MapOrigin.in(world);
                draft.setPos1(origin.clone());
                draft.setPos2(new Location(world,
                        origin.getX() + dims.x() - 1,
                        origin.getY() + dims.y() - 1,
                        origin.getZ() + dims.z() - 1));
                draft.setMinPlayers(def.minPlayers());
                draft.setMaxPlayers(def.maxPlayers());
                if (def.waitingSpawn() != null) {
                    draft.setWaitingSpawn(absolutize(def.waitingSpawn(), origin));
                }
                for (SpawnPoint sp : def.participantSpawns()) {
                    draft.addParticipantSpawn(absolutize(sp, origin));
                }
                int ox = origin.getBlockX();
                int oy = origin.getBlockY();
                int oz = origin.getBlockZ();
                for (Map.Entry<String, Object> entry : def.extensions().entrySet()) {
                    MapExtension<?> ext = extensionRegistry.byId(entry.getKey()).orElse(null);
                    if (ext == null) {
                        draft.setRawExtension(entry.getKey(), entry.getValue());
                        continue;
                    }
                    draft.setRawExtension(entry.getKey(), toAbsolute(ext, entry.getValue(), ox, oy, oz));
                }
                drafts.put(player.getUniqueId(), draft);
                player.teleport(origin);
                player.setGameMode(org.bukkit.GameMode.CREATIVE);
                return draft;
            } catch (com.sk89q.worldedit.WorldEditException e) {
                destroyEditWorld(worldName);
                throw new IOException("Schematic paste failed: " + e.getMessage(), e);
            }
        } else {
            MapDraft draft = new MapDraft(player.getUniqueId(), gameId, mapId, worldName, pre);
            drafts.put(player.getUniqueId(), draft);
            player.teleport(MapOrigin.in(world));
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            return draft;
        }
    }

    /**
     * Returns a list of human-readable issues that would prevent a successful save.
     * Empty list = draft is save-able. Order is relevant for the command to show
     * the user which steps are still missing.
     */
    public List<String> validateDraft(Player player) {
        MapDraft draft = drafts.get(player.getUniqueId());
        if (draft == null)
            return List.of("no-active-draft");
        List<String> issues = new ArrayList<>();
        if (draft.pos1().isEmpty())
            issues.add("pos1");
        if (draft.pos2().isEmpty())
            issues.add("pos2");
        if (draft.participantSpawns().isEmpty())
            issues.add("participant-spawns");
        if (draft.waitingSpawn().isEmpty())
            issues.add("waiting-spawn");
        // Any extension marked required but not set on the draft
        for (MapExtension<?> ext : extensionRegistry.all()) {
            if (ext.required() && !draft.extensions().containsKey(ext.key().id())) {
                issues.add("extension:" + ext.key().id());
            }
        }
        return issues;
    }

    /** Captures the region, persists the map, and tears down the edit world. */
    public MapDefinition save(Player player) throws IOException {
        MapDraft draft = drafts.get(player.getUniqueId());
        if (draft == null)
            throw new IllegalStateException("No active draft.");

        List<String> issues = validateDraft(player);
        if (!issues.isEmpty()) {
            throw new IllegalStateException("missing:" + String.join(",", issues));
        }

        World editWorld = Bukkit.getWorld(draft.editWorldName());
        if (editWorld == null)
            throw new IllegalStateException("Edit world not loaded: " + draft.editWorldName());

        persistence.ensureDirectories();
        Path schematicFile = persistence.schematicsDir().resolve(draft.mapId() + ".schem");

        BlockVector3 a = toBlockVector(draft.pos1().get());
        BlockVector3 b = toBlockVector(draft.pos2().get());

        BlockVector3 min;
        try {
            min = WorldEditPaster.saveRegion(editWorld, a, b, schematicFile);
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException("Schematic capture failed: " + e.getMessage(), e);
        }

        SpawnPoint waitingDelta = draft.waitingSpawn()
                .map(s -> relativize(s, min))
                .orElse(null);
        List<SpawnPoint> participantDeltas = draft.participantSpawns().stream()
                .map(s -> relativize(s, min))
                .toList();
        int minX = min.x();
        int minY = min.y();
        int minZ = min.z();
        Map<String, Object> extensionDeltas = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : draft.extensions().entrySet()) {
            MapExtension<?> ext = extensionRegistry.byId(entry.getKey()).orElse(null);
            if (ext == null) {
                extensionDeltas.put(entry.getKey(), entry.getValue());
                continue;
            }
            extensionDeltas.put(entry.getKey(), toRelative(ext, entry.getValue(), minX, minY, minZ));
        }

        MapDefinition def = new MapDefinition(draft.gameId(), draft.mapId(), schematicFile,
                waitingDelta, participantDeltas, extensionDeltas,
                draft.minPlayers(), draft.maxPlayers());
        persistence.save(def);
        mapService.register(def);

        teleportOutAndTeardown(player, draft);
        drafts.remove(player.getUniqueId());
        return def;
    }

    /**
     * Drops the draft, teleports player out, tears down the edit world. No map is
     * saved.
     */
    public void discard(Player player) {
        MapDraft draft = drafts.remove(player.getUniqueId());
        if (draft == null)
            return;
        teleportOutAndTeardown(player, draft);
    }

    public void delete(GameId gameId, String mapId) throws IOException {
        persistence.delete(gameId, mapId);
        mapService.unregister(gameId, mapId);
        Path schematicFile = persistence.schematicsDir().resolve(mapId + ".schem");
        Files.deleteIfExists(schematicFile);
    }

    public MapPersistence persistence() {
        return persistence;
    }

    public MapService mapService() {
        return mapService;
    }

    /** Tears down any edit worlds still on disk at startup (e.g. from a crash). */
    public void purgeOrphanedEditWorlds() {
        Path container = Bukkit.getWorldContainer().toPath();
        try (var stream = Files.list(container)) {
            for (Path p : stream.toList()) {
                if (!Files.isDirectory(p))
                    continue;
                String name = p.getFileName().toString();
                if (!name.startsWith("tea_edit_"))
                    continue;
                World loaded = Bukkit.getWorld(name);
                if (loaded != null)
                    continue;
                try {
                    deleteRecursive(p);
                    logger.info("Removed orphaned edit world " + name);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to delete orphaned edit world " + name, e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to scan world container for orphaned edit worlds", e);
        }
    }

    private String editWorldName(Player player, GameId gameId, String mapId) {
        String uuidFragment = player.getUniqueId().toString().substring(0, 8);
        String safeMap = mapId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return "tea_edit_" + uuidFragment + "_" + gameId.value() + "_" + safeMap;
    }

    private World createEditWorld(String name) {
        WorldCreator creator = new WorldCreator(name)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .generateStructures(false);
        World world = creator.createWorld();
        if (world == null)
            throw new IllegalStateException("Failed to create edit world " + name);
        world.setSpawnLocation(MapOrigin.X, MapOrigin.Y, MapOrigin.Z);
        world.setAutoSave(false);
        return world;
    }

    private void teleportOutAndTeardown(Player player, MapDraft draft) {
        Location back = draft.preEditLocation();
        if (back == null || back.getWorld() == null
                || back.getWorld().getName().equals(draft.editWorldName())) {
            World fallback = Bukkit.getWorlds().stream()
                    .filter(w -> !w.getName().startsWith("tea_edit_")
                            && !w.getName().startsWith("tea_arena_"))
                    .findFirst()
                    .orElse(Bukkit.getWorlds().get(0));
            back = fallback.getSpawnLocation();
        }
        player.teleport(back);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        destroyEditWorld(draft.editWorldName());
    }

    private void destroyEditWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return;
        for (Player p : world.getPlayers()) {
            World fallback = Bukkit.getWorlds().stream()
                    .filter(w -> !w.getName().startsWith("tea_edit_"))
                    .findFirst()
                    .orElse(Bukkit.getWorlds().get(0));
            p.teleport(fallback.getSpawnLocation());
        }
        Path folder = world.getWorldFolder().toPath();
        if (!Bukkit.unloadWorld(world, false)) {
            logger.warning("Failed to unload edit world " + worldName + "; folder not deleted.");
            return;
        }
        try {
            deleteRecursive(folder);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete edit world folder " + folder, e);
        }
    }

    private static BlockVector3 toBlockVector(Location loc) {
        return BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object toAbsolute(MapExtension<?> ext, Object value, int ox, int oy, int oz) {
        return ((MapExtension) ext).toAbsolute(value, ox, oy, oz);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object toRelative(MapExtension<?> ext, Object value, int mx, int my, int mz) {
        return ((MapExtension) ext).toRelative(value, mx, my, mz);
    }

    /**
     * Converts a delta-based SpawnPoint into an absolute location spawn, anchored
     * at the given origin.
     */
    private static SpawnPoint absolutize(SpawnPoint delta, Location origin) {
        return new SpawnPoint(delta.id(),
                origin.getX() + delta.x(),
                origin.getY() + delta.y(),
                origin.getZ() + delta.z(),
                delta.yaw(), delta.pitch());
    }

    /** Converts an absolute-location SpawnPoint into a delta from min-corner. */
    private static SpawnPoint relativize(SpawnPoint absolute, BlockVector3 min) {
        return new SpawnPoint(absolute.id(),
                absolute.x() - min.x(),
                absolute.y() - min.y(),
                absolute.z() - min.z(),
                absolute.yaw(), absolute.pitch());
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
