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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Each session runs in its own temporary void world {@code tea_edit_<playerShortUuid>_<game>_<map>}. */
public final class MapEditingService {

    private final Plugin plugin;
    private final MapService mapService;
    private final MapPersistence persistence;
    private final MapExtensionRegistryImpl extensionRegistry;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, MapDraft> drafts = new ConcurrentHashMap<>();
    private MapBackups backups;

    public MapEditingService(Plugin plugin, MapService mapService, MapPersistence persistence,
            MapExtensionRegistryImpl extensionRegistry, Logger logger) {
        this.plugin = plugin;
        this.mapService = mapService;
        this.persistence = persistence;
        this.extensionRegistry = extensionRegistry;
        this.logger = logger;
    }

    public void setBackups(MapBackups backups) {
        this.backups = backups;
    }

    public MapBackups backups() {
        return backups;
    }

    public void loadAllAndRegister() {
        for (MapDefinition def : persistence.loadAll()) {
            mapService.register(def);
        }
    }

    public Optional<MapDraft> currentDraft(Player player) {
        return Optional.ofNullable(drafts.get(player.getUniqueId()));
    }

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
        fireStarted(player, draft);
        return draft;
    }

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
                SchematicStore.current().paste(def.schematicFile(), world, MapOrigin.X, MapOrigin.Y, MapOrigin.Z);

                int[] dims = SchematicStore.current().readDimensions(def.schematicFile());
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
                fireStarted(player, draft);
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
            fireStarted(player, draft);
            return draft;
        }
    }

    /** Order matters: the command lists the missing steps in this order. */
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
        for (MapExtension<?> ext : extensionsFor(draft.gameId())) {
            if (ext.required() && !draft.extensions().containsKey(ext.key().id())) {
                issues.add("extension:" + ext.key().id());
            }
        }
        return issues;
    }

    /** Carries bundle keys rather than text so the scoreboard renders each viewer's locale. */
    public record SetupStep(String labelKey, String hintKey, Map<String, String> placeholders) {
    }

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
        Path schematicFile = persistence.schematicsDir().resolve(draft.mapId() + "." + SchematicStore.current().extension());
        if (backups != null) {
            backups.snapshot(draft.gameId(), draft.mapId(), player.getName(), "save");
        }

        Location p1 = draft.pos1().get();
        Location p2 = draft.pos2().get();

        int[] min;
        try {
            min = SchematicStore.current().saveRegion(editWorld,
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
        me.playbosswar.tea.core.impl.map.MapChanges.changed();
        return def;
    }

    public void discard(Player player) {
        MapDraft draft = drafts.remove(player.getUniqueId());
        if (draft == null)
            return;
        teleportOutAndTeardown(player, draft);
    }

    public void delete(GameId gameId, String mapId) throws IOException {
        delete(gameId, mapId, "console");
    }

    /** A backup is kept, so {@link #restore} can bring the map back. */
    public void delete(GameId gameId, String mapId, String actor) throws IOException {
        if (backups != null) {
            backups.snapshot(gameId, mapId, actor, "delete");
        }
        persistence.delete(gameId, mapId);
        mapService.unregister(gameId, mapId);
        // Either format may exist (saved on a modern or a legacy server).
        Files.deleteIfExists(persistence.schematicsDir().resolve(mapId + ".schem"));
        Path schematicFile = persistence.schematicsDir().resolve(mapId + ".schematic");
        Files.deleteIfExists(schematicFile);
        me.playbosswar.tea.core.impl.map.MapChanges.changed();
    }

    /**
     * Arenas already running keep the map they started with.
     *
     * @throws IllegalStateException "editing" if someone is editing that map,
     *                               "no-version" if there is no such version.
     */
    public MapDefinition restore(GameId gameId, String mapId, int number, String actor) throws IOException {
        if (backups == null) {
            throw new IllegalStateException("no-version");
        }
        for (MapDraft draft : drafts.values()) {
            if (draft.gameId().equals(gameId) && draft.mapId().equals(mapId)) {
                throw new IllegalStateException("editing");
            }
        }
        MapBackups.Version version = backups.get(gameId, mapId, number)
                .orElseThrow(() -> new IllegalStateException("no-version"));
        backups.restore(gameId, mapId, version, actor);
        MapDefinition def = persistence.loadForGame(gameId).stream()
                .filter(d -> d.mapId().equals(mapId)).findFirst()
                .orElseThrow(() -> new IOException("Restored map could not be read back"));
        mapService.register(def);
        MapChanges.changed();
        return def;
    }

    /** Sorted by id, independent of registration order. */
    public List<MapExtension<?>> extensionsFor(GameId gameId) {
        List<MapExtension<?>> out = new ArrayList<>();
        for (MapExtension<?> ext : extensionRegistry.all()) {
            if (ext.appliesTo(gameId)) {
                out.add(ext);
            }
        }
        out.sort(java.util.Comparator.comparing(e -> e.key().id()));
        return out;
    }

    public interface DraftListener {
        void started(Player player, MapDraft draft);

        void ended(Player player, MapDraft draft);
    }

    private final List<DraftListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addListener(DraftListener listener) {
        listeners.add(listener);
    }

    private void fireStarted(Player player, MapDraft draft) {
        for (DraftListener l : listeners) {
            try {
                l.started(player, draft);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Draft listener failed", e);
            }
        }
    }

    private void fireEnded(Player player, MapDraft draft) {
        for (DraftListener l : listeners) {
            try {
                l.ended(player, draft);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Draft listener failed", e);
            }
        }
    }

    public MapPersistence persistence() {
        return persistence;
    }

    public MapService mapService() {
        return mapService;
    }

    /** Removes edit worlds left on disk by a crash. */
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
        WorldCreator creator = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().worlds().voidWorld(name)
                .environment(World.Environment.NORMAL)
                .generateStructures(false);
        World world = me.playbosswar.tea.core.impl.arena.ArenaWorlds.create(creator);
        if (world == null)
            throw new IllegalStateException("Failed to create edit world " + name);
        world.setSpawnLocation(MapOrigin.X, MapOrigin.Y, MapOrigin.Z);
        world.setAutoSave(false);
        return world;
    }

    private void teleportOutAndTeardown(Player player, MapDraft draft) {
        fireEnded(player, draft);
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

    private static SpawnPoint absolutize(SpawnPoint delta, Location origin) {
        return new SpawnPoint(delta.id(),
                origin.getX() + delta.x(),
                origin.getY() + delta.y(),
                origin.getZ() + delta.z(),
                delta.yaw(), delta.pitch());
    }

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
