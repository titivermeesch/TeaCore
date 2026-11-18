package me.playbosswar.tea.core.impl.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.game.ArenaWorldConfig;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.game.MapSelectionPolicy;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import me.playbosswar.tea.core.impl.map.MapOrigin;
import me.playbosswar.tea.core.impl.map.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class ArenaManagerImpl implements ArenaManager {

    private final Plugin plugin;
    private final GameRegistry gameRegistry;
    private final MapService mapService;
    private final Logger logger;
    private final ConcurrentHashMap<ArenaId, ArenaImpl> arenas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ArenaId> playerArena = new ConcurrentHashMap<>();

    /** Set after construction (TeamManagerImpl itself depends on this manager). */
    private me.playbosswar.tea.core.api.team.TeamManager teamManager;

    public void setTeamManager(me.playbosswar.tea.core.api.team.TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public ArenaManagerImpl(Plugin plugin, GameRegistry gameRegistry, MapService mapService, Logger logger) {
        this.plugin = plugin;
        this.gameRegistry = gameRegistry;
        this.mapService = mapService;
        this.logger = logger;
    }

    @Override
    public Arena allocate(GameId gameId, @Nullable String forcedMapId) {
        GameDefinition def = gameRegistry.get(gameId)
                .orElseThrow(() -> new IllegalStateException("Game not registered: " + gameId));

        MapDefinition mapDef = pickMap(def, forcedMapId);
        if (mapDef == null) {
            throw new IllegalStateException("No maps registered for " + gameId);
        }

        ArenaId arenaId = ArenaId.random();
        ArenaImpl arena = new ArenaImpl(arenaId, gameId, mapDef.mapId(), mapDef.minPlayers(), mapDef.maxPlayers());
        arenas.put(arenaId, arena);

        World world = createVoidWorld(arenaId, def.arenaWorld());
        arena.setWorld(world);

        mapService.paste(mapDef, world);

        transition(arena, ArenaState.WAITING, def.lifecycle().maxWaitSeconds());
        logger.info("Allocated arena " + arenaId + " for " + gameId + " map=" + mapDef.mapId());
        return arena;
    }

    @Override
    public void release(ArenaId id) {
        ArenaImpl arena = arenas.remove(id);
        if (arena == null)
            return;
        transition(arena, ArenaState.RESETTING, -1);

        evacuate(arena);

        Optional<World> maybeWorld = arena.world();
        if (maybeWorld.isPresent()) {
            World world = maybeWorld.get();
            Bukkit.unloadWorld(world, false);
            try {
                Path folder = world.getWorldFolder().toPath();
                deleteRecursive(folder);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to delete world folder for " + id, e);
            }
        }

        transition(arena, ArenaState.RELEASED, -1);
        logger.info("Released arena " + id);
    }

    @Override
    public Optional<Arena> get(ArenaId id) {
        return Optional.ofNullable(arenas.get(id));
    }

    @Override
    public Optional<Arena> arenaOf(UUID playerUuid) {
        ArenaId id = playerArena.get(playerUuid);
        return id == null ? Optional.empty() : Optional.ofNullable(arenas.get(id));
    }

    @Override
    public Collection<Arena> all() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    @Override
    public boolean addPlayer(ArenaId id, Player player, boolean asSpectator) {
        ArenaImpl arena = arenas.get(id);
        if (arena == null)
            return false;
        if (!asSpectator && arena.players().size() >= arena.maxPlayers())
            return false;

        ArenaId existing = playerArena.get(player.getUniqueId());
        if (existing != null && !existing.equals(id)) {
            removePlayer(player, ArenaPlayerLeaveEvent.LeaveReason.COMMAND);
        }

        boolean added = asSpectator ? arena.addSpectator(player.getUniqueId()) : arena.addPlayer(player.getUniqueId());
        if (!added)
            return false;
        playerArena.put(player.getUniqueId(), id);

        teleportIn(arena, player, asSpectator);

        Bukkit.getPluginManager().callEvent(new ArenaPlayerJoinEvent(arena, player, asSpectator));
        return true;
    }

    @Override
    public boolean removePlayer(Player player, ArenaPlayerLeaveEvent.LeaveReason reason) {
        ArenaId id = playerArena.remove(player.getUniqueId());
        if (id == null)
            return false;
        ArenaImpl arena = arenas.get(id);
        if (arena == null)
            return false;
        arena.removePlayer(player.getUniqueId());
        teleportOut(player, arena);
        Bukkit.getPluginManager().callEvent(new ArenaPlayerLeaveEvent(arena, player, reason));
        return true;
    }

    @Override
    public void endMatch(ArenaId id, @Nullable UUID winnerUuid) {
        end(id, winnerUuid, null);
    }

    @Override
    public void endMatchForTeam(ArenaId id, @Nullable me.playbosswar.tea.core.api.team.TeamId winningTeam) {
        UUID representative = null;
        ArenaImpl arena = arenas.get(id);
        if (arena != null && winningTeam != null && teamManager != null) {
            for (UUID uuid : arena.players()) {
                if (teamManager.teamOf(id, uuid).map(t -> t.id().equals(winningTeam)).orElse(false)) {
                    representative = uuid;
                    break;
                }
            }
        }
        end(id, representative, winningTeam);
    }

    private void end(ArenaId id, @Nullable UUID winnerUuid,
                     @Nullable me.playbosswar.tea.core.api.team.TeamId winningTeam) {
        ArenaImpl arena = arenas.get(id);
        if (arena == null)
            return;
        // Match-end is only meaningful from LIVE — ending a match that hasn't
        // gone live yet (e.g. the last player drops out during COUNTDOWN)
        // shouldn't crown a winner. The lifecycle ticker handles
        // COUNTDOWN -> WAITING aborts on its own; game plugins reacting to
        // player-leave events shouldn't be able to short-circuit that with
        // a stray endMatch(...) call.
        if (arena.state() != ArenaState.LIVE) {
            logger.warning("Ignoring endMatch on arena " + id.value()
                    + " in state " + arena.state()
                    + " (only valid from LIVE). Use arenaManager.release(id) to tear down "
                    + "an arena that hasn't started.");
            return;
        }
        GameDefinition def = gameRegistry.get(arena.gameId()).orElse(null);
        if (def == null) {
            logger.log(Level.SEVERE,
                    "Couldn't find game definition for " + arena.gameId() + " when ending match in " + id);
            return;
        }
        transition(arena, ArenaState.ENDING, def.lifecycle().endingSeconds());
        Bukkit.getPluginManager().callEvent(new MatchEndEvent(arena, winnerUuid, winningTeam));
    }

    @Override
    public void forceStart(ArenaId id) {
        ArenaImpl arena = arenas.get(id);
        if (arena == null)
            return;
        // Collapsing the phase timer causes LifecycleTicker to progress the state next
        // tick: WAITING -> COUNTDOWN, COUNTDOWN -> STARTING, STARTING -> LIVE.
        arena.setPhaseSecondsRemaining(0);
    }

    @Override
    public boolean observe(ArenaId id, Player player) {
        ArenaImpl arena = arenas.get(id);
        if (arena == null || arena.world().isEmpty())
            return false;
        Location origin = MapOrigin.in(arena.world().get());
        player.teleport(origin);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        return true;
    }

    public void handleDisconnect(UUID uuid) {
        ArenaId id = playerArena.remove(uuid);
        if (id == null)
            return;
        ArenaImpl arena = arenas.get(id);
        if (arena == null)
            return;
        arena.removePlayer(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            Bukkit.getPluginManager().callEvent(
                    new ArenaPlayerLeaveEvent(arena, p, ArenaPlayerLeaveEvent.LeaveReason.DISCONNECT));
        }
    }

    /** Called by {@link LifecycleTicker} to move an arena to its next state. */
    void transition(ArenaImpl arena, ArenaState to, int phaseSeconds) {
        ArenaState from = arena.state();
        arena.setState(to);
        arena.setPhaseSecondsRemaining(phaseSeconds);
        Bukkit.getPluginManager().callEvent(new ArenaStateChangeEvent(arena, from, to));
    }

    private MapDefinition pickMap(GameDefinition def, @Nullable String forcedMapId) {
        if (forcedMapId != null) {
            return mapService.get(def.id(), forcedMapId).orElseThrow(
                    () -> new IllegalArgumentException("Map not registered for " + def.id() + ": " + forcedMapId));
        }
        MapSelectionPolicy policy = def.mapSelection();
        if (policy instanceof MapSelectionPolicy.Fixed fixed) {
            return mapService.get(def.id(), fixed.mapId())
                    .orElseThrow(() -> new IllegalStateException("Fixed map missing: " + fixed.mapId()));
        }
        List<MapDefinition> candidates = List.copyOf(mapService.forGame(def.id()));
        if (candidates.isEmpty())
            return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private World createVoidWorld(ArenaId arenaId, ArenaWorldConfig worldConfig) {
        String worldName = "tea_arena_" + arenaId.value().toString().replace('-', '_');
        WorldCreator creator = new WorldCreator(worldName)
                .generator(new VoidChunkGenerator())
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .generateStructures(false);
        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create arena world " + worldName);
        }
        world.setSpawnLocation(0, 64, 0);
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);

        // Touch an empty data/raids.dat. Vanilla / Paper's world-save path
        // tries to read this file on unload even when autoSave is off, and
        // freshly-generated arena worlds never have any raids — so without
        // the empty file, every match-end logs a noisy
        // "java.nio.file.NoSuchFileException: ./tea_arena_<id>/data/raids.dat".
        // Pre-creating a zero-byte file lets the save attempt no-op.
        try {
            Path dataDir = world.getWorldFolder().toPath().resolve("data");
            Files.createDirectories(dataDir);
            Path raids = dataDir.resolve("raids.dat");
            if (!Files.exists(raids)) {
                Files.createFile(raids);
            }
        } catch (IOException ioe) {
            logger.log(Level.FINE,
                    "Could not pre-create raids.dat for " + worldName + " (harmless): " + ioe.getMessage());
        }

        world.setDifficulty(worldConfig.difficulty());
        world.setSpawnFlags(worldConfig.allowMonsters(), worldConfig.allowAnimals());
        for (var entry : worldConfig.gameRules().entrySet()) {
            applyGameRule(world, entry.getKey(), entry.getValue());
        }
        if (worldConfig.timeOfDay() != null) {
            world.setTime(worldConfig.timeOfDay());
        }
        if (worldConfig.storm() != null) {
            world.setStorm(worldConfig.storm());
        }
        if (worldConfig.thundering() != null) {
            world.setThundering(worldConfig.thundering());
        }
        return world;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyGameRule(World world, GameRule<?> rule, Object value) {
        world.setGameRule((GameRule) rule, value);
    }

    private void teleportIn(ArenaImpl arena, Player player, boolean asSpectator) {
        Optional<Location> target = resolveWaitingSpawn(arena);
        if (target.isEmpty()) return;
        player.teleport(target.get());
        ArenaWorldConfig cfg = gameRegistry.get(arena.gameId())
                .map(GameDefinition::arenaWorld)
                .orElseGet(() -> ArenaWorldConfig.defaults().build());
        player.setGameMode(asSpectator ? cfg.spectatorGameMode() : cfg.playerGameMode());
    }

    /**
     * Returns the location the matchmaker would teleport a joining player to
     * for the given arena. Used both by {@link #teleportIn} and by the
     * pre-spawn hook so a cross-server-routed player materializes directly
     * in the arena's waiting room instead of flickering through the
     * destination server's overworld first.
     *
     * @return the waiting-spawn location, or empty if the arena has no
     *         live world yet (it may still be allocating).
     */
    public Optional<Location> resolveWaitingSpawn(ArenaId id) {
        ArenaImpl arena = arenas.get(id);
        return arena == null ? Optional.empty() : resolveWaitingSpawn(arena);
    }

    private Optional<Location> resolveWaitingSpawn(ArenaImpl arena) {
        Optional<World> world = arena.world();
        if (world.isEmpty()) return Optional.empty();
        Optional<MapDefinition> mapDef = arena.mapId()
                .flatMap(mid -> mapService.get(arena.gameId(), mid));
        if (mapDef.isPresent() && mapDef.get().waitingSpawn() != null) {
            return Optional.of(mapDef.get().waitingSpawn().toLocation(
                    me.playbosswar.tea.core.impl.map.MapOrigin.in(world.get())));
        }
        return Optional.of(new Location(world.get(), 0, 64, 0));
    }

    void teleportOut(Player player, @Nullable ArenaImpl arena) {
        // In cross-server modes, send the player back to the lobby backend via the
        // proxy
        // rather than leaving them stranded on this arena backend.
        try {
            me.playbosswar.tea.core.api.TeaCoreAPI core = me.playbosswar.tea.core.api.TeaCoreAPI.get();
            if (!core.isStandalone()) {
                String lobby = core.lobbyServerName();
                if (lobby != null && !lobby.isBlank()) {
                    core.proxyTransport().connect(player, lobby);
                    return;
                }
            }
        } catch (IllegalStateException ignored) {
            // API not initialized yet (shouldn't happen in practice); fall through to local
            // teleport
        }

        World fallback = Bukkit.getWorlds().stream()
                .filter(w -> !w.getName().startsWith("tea_arena_"))
                .findFirst()
                .orElse(Bukkit.getWorlds().get(0));
        player.teleport(fallback.getSpawnLocation());
        ArenaWorldConfig cfg = arena == null
                ? ArenaWorldConfig.defaults().build()
                : gameRegistry.get(arena.gameId())
                        .map(GameDefinition::arenaWorld)
                        .orElseGet(() -> ArenaWorldConfig.defaults().build());
        player.setGameMode(cfg.leaveGameMode());
    }

    void evacuate(ArenaImpl arena) {
        for (UUID uuid : arena.allOccupants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                teleportOut(p, arena);
                playerArena.remove(uuid);
                Bukkit.getPluginManager().callEvent(
                        new ArenaPlayerLeaveEvent(arena, p, ArenaPlayerLeaveEvent.LeaveReason.MATCH_ENDED));
            }
        }
    }

    /** Provides {@link LifecycleTicker} visibility into arena state. */
    Collection<ArenaImpl> arenasForTicker() {
        return arenas.values();
    }

    GameRegistry gameRegistry() {
        return gameRegistry;
    }

    Plugin plugin() {
        return plugin;
    }

    List<SpawnPoint> participantSpawns(Arena arena) {
        return arena.mapId()
                .flatMap(m -> mapService.get(arena.gameId(), m))
                .map(MapDefinition::participantSpawns)
                .orElse(List.of(new SpawnPoint("fallback", 0, 64, 0, 0f, 0f)));
    }

    @Override
    public List<Location> participantSpawnLocations(Arena arena) {
        if (arena.world().isEmpty())
            return List.of();
        Location origin = me.playbosswar.tea.core.impl.map.MapOrigin.in(arena.world().get());
        List<SpawnPoint> spawns = participantSpawns(arena);
        List<Location> out = new java.util.ArrayList<>(spawns.size());
        for (SpawnPoint sp : spawns)
            out.add(sp.toLocation(origin));
        return out;
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
