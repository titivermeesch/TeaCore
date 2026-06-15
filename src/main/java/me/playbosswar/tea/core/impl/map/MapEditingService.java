package me.playbosswar.tea.core.impl.map;

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

                int[] dims = WorldEditPaster.readDimensions(def.schematicFile());
                MapDraft draft = new MapDraft(player.getUniqueId(), gameId, mapId, worldName, pre);
                Location origin = MapOrigin.in(world);
                draft.setPos1(origin.clone());
                draft.setPos2(new Location(world,
                        origin.getX() + dims[0] - 1,
                        origin.getY() + dims[1] - 1,
                        origin.getZ() + dims[2] - 1));
                draft.setMinPlayers(def.minPlayers());
                draft.setMaxPlayers(def.maxPlayers());
                draft.setDisplayName(def.displayName());
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
            } catch (Exception e) {
                // Catch broadly (not WorldEditException) so this class carries no
                // WorldEdit type references and can load on servers without FAWE.
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

    /**
     * One remaining authoring step for the map-setup sidebar. Carries the
     * message-bundle keys (resolved per-viewer locale by the scoreboard) plus
     * the placeholders they need, rather than pre-rendered text.
     *
     * @param labelKey     bundle key for the human-readable step label.
     * @param hintKey      bundle key for the command hint that completes it.
     * @param placeholders placeholders for both lines (e.g. extension id /
     *                     subcommand); never {@code null}.
     */
    public record SetupStep(String labelKey, String hintKey, Map<String, String> placeholders) {
    }

    /**
     * Returns the ordered list of setup steps the player still has to complete
     * before the draft is save-able, derived from {@link #validateDraft(Player)}.
     * Empty when the draft is ready (or there is no active draft). Used by the
     * map-setup sidebar; chat output uses {@code validateDraft} directly.
     */
    public List<SetupStep> remainingSteps(Player player) {
        List<SetupStep> steps = new ArrayList<>();
        for (String field : validateDraft(player)) {
            if (field.equals("no-active-draft"))
                continue;
            if (field.startsWith("extension:")) {
                String id = field.substring("extension:".length());
                MapExtension<?> ext = extensionRegistry.byId(id).orElse(null);
                String subcommand = ext != null ? ext.subcommand() : id;
                String label = ext != null ? ext.statusLabel() : id;
                steps.add(new SetupStep(
                        "core.map.scoreboard.step.extension.label",
                        "core.map.scoreboard.step.extension.hint",
                        Map.of("id", id, "label", label, "subcommand", subcommand)));
            } else {
                steps.add(new SetupStep(
                        "core.map.scoreboard.step." + field + ".label",
                        "core.map.scoreboard.step." + field + ".hint",
                        Map.of()));
            }
        }
        return steps;
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

        Location p1 = draft.pos1().get();
        Location p2 = draft.pos2().get();

        int[] min;
        try {
            min = WorldEditPaster.saveRegion(editWorld,
                    p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                    p2.getBlockX(), p2.getBlockY(), p2.getBlockZ(), schematicFile);
        } catch (Exception e) {
            // Broad catch keeps WorldEditException out of this class's bytecode.
            throw new IOException("Schematic capture failed: " + e.getMessage(), e);
        }
        int minX = min[0];
        int minY = min[1];
        int minZ = min[2];

        SpawnPoint waitingDelta = draft.waitingSpawn()
                .map(s -> relativize(s, minX, minY, minZ))
                .orElse(null);
        List<SpawnPoint> participantDeltas = draft.participantSpawns().stream()
                .map(s -> relativize(s, minX, minY, minZ))
                .toList();
        Map<String, Object> extensionDeltas = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : draft.extensions().entrySet()) {
            MapExtension<?> ext = extensionRegistry.byId(entry.getKey()).orElse(null);
            if (ext == null) {
                extensionDeltas.put(entry.getKey(), entry.getValue());
                continue;
            }
            extensionDeltas.put(entry.getKey(), toRelative(ext, entry.getValue(), minX, minY, minZ));
        }

        MapDefinition def = new MapDefinition(draft.gameId(), draft.mapId(),
                draft.displayName().orElse(null), schematicFile,
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
    private static SpawnPoint relativize(SpawnPoint absolute, int minX, int minY, int minZ) {
        return new SpawnPoint(absolute.id(),
                absolute.x() - minX,
                absolute.y() - minY,
                absolute.z() - minZ,
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
