package me.playbosswar.tea.core.impl.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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
        this.waitingAreas = new WaitingAreas(plugin);
        this.waitingRooms = new WaitingRooms(plugin, logger);
        this.waitingRooms.attach(this);
        Bukkit.getPluginManager().registerEvents(waitingRooms, plugin);
    }

    private final WaitingRooms waitingRooms;

    private Hosting hosting;

    public void setHosting(Hosting hosting) {
        this.hosting = hosting;
    }

    public @Nullable Hosting hosting() {
        return hosting;
    }

    public WaitingRooms waitingRooms() {
        return waitingRooms;
    }

    private final WaitingAreas waitingAreas;

    public WaitingAreas waitingAreas() {
        return waitingAreas;
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
        if (forcedMapId != null) {
            arena.lockMap();
        }
        arenas.put(arenaId, arena);

        long t0 = System.nanoTime();
        World world = takeSpare(gameId, mapDef.mapId(), def.arenaWorld());
        boolean reused = world != null;
        if (world == null) {
            world = createVoidWorld(arenaId, def.arenaWorld());
        }
        arena.setWorld(world);
        WaitingRooms.Room room = waitingRooms.roomFor(gameId, mapDef.mapId()).orElse(null);
        arena.setWaitingRoom(room);
        long t1 = System.nanoTime();
        String worldNote = (reused ? "reused world " : "world ") + ms(t0, t1) + " ms";

        World target = world;
        if (asyncPaste()) {
            // FAWE pastes off the main thread; the arena stays ALLOCATING (the
            // matchmaker skips it) until the map is in place.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                long p0 = System.nanoTime();
                mapService.paste(mapDef, target);
                if (room != null) {
                    waitingRooms.paste(room, target);
                }
                long p1 = System.nanoTime();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (arenas.get(arenaId) != arena || arena.state() != ArenaState.ALLOCATING) {
                        return; // released while pasting
                    }
                    transition(arena, ArenaState.WAITING, def.lifecycle().maxWaitSeconds());
                    logger.info("Allocated arena " + arenaId + " for " + gameId + " map=" + mapDef.mapId()
                            + " (" + worldNote + ", async paste " + ms(p0, p1) + " ms)");
                    admitPending(arena);
                });
            });
            return arena;
        }
        mapService.paste(mapDef, world);
        if (room != null) {
            waitingRooms.paste(room, world);
        }
        long t2 = System.nanoTime();

        transition(arena, ArenaState.WAITING, def.lifecycle().maxWaitSeconds());
        logger.info("Allocated arena " + arenaId + " for " + gameId + " map=" + mapDef.mapId()
                + " (" + worldNote + ", paste " + ms(t1, t2) + " ms)");
        return arena;
    }

    /** How long a released arena waits for players to finish their proxy hop to the lobby. */
    private static final int RELEASE_WAIT_TICKS = 100;

    @Override
    public void release(ArenaId id) {
        pendingJoins.remove(id);
        ArenaImpl arena = arenas.remove(id);
        if (arena == null)
            return;
        transition(arena, ArenaState.RESETTING, -1);

        long t0 = System.nanoTime();
        evacuate(arena);
        long t1 = System.nanoTime();
        // Behind a proxy, evacuate() only asks the proxy to move players to the
        // lobby; they stay in this world for a moment. Unloading or resetting
        // the world now would drop them at the main world's spawn first (the
        // "overworld" flash). Wait until they're gone, up to 5 seconds.
        if (arena.world().map(w -> !w.getPlayers().isEmpty()).orElse(false)) {
            awaitEmptyThenFinish(arena, id, t0, t1, 0);
            return;
        }
        finishRelease(arena, id, t0, t1);
    }

    private void awaitEmptyThenFinish(ArenaImpl arena, ArenaId id, long t0, long t1, int waited) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean occupied = arena.world().map(w -> !w.getPlayers().isEmpty()).orElse(false);
            if (occupied && waited + 5 < RELEASE_WAIT_TICKS) {
                awaitEmptyThenFinish(arena, id, t0, t1, waited + 5);
            } else {
                finishRelease(arena, id, t0, t1);
            }
        }, 5L);
    }

    private void finishRelease(ArenaImpl arena, ArenaId id, long t0, long t1) {
        long t2 = t1;
        long t3 = t1;

        Optional<World> maybeWorld = arena.world();
        if (maybeWorld.isPresent() && keepSpare(arena, maybeWorld.get())) {
            t2 = t3 = System.nanoTime();
        } else if (maybeWorld.isPresent()) {
            World world = maybeWorld.get();
            Bukkit.unloadWorld(world, false);
            t2 = System.nanoTime();
            try {
                Path folder = world.getWorldFolder().toPath();
                deleteRecursive(folder);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to delete world folder for " + id, e);
            }
            t3 = System.nanoTime();
        }

        transition(arena, ArenaState.RELEASED, -1);
        logger.info("Released arena " + id + " (evacuate " + ms(t0, t1) + " ms, unload " + ms(t1, t2)
                + " ms, delete " + ms(t2, t3) + " ms)");
    }

    private record Spare(World world, GameId game, String mapId, ArenaWorldConfig config) { }

    private final java.util.Deque<Spare> spares = new java.util.ArrayDeque<>();

    public int spareCount() {
        return spares.size();
    }

    /** {@code arena-pool.spare-worlds}: how many released worlds to keep; 0 turns reuse off. */
    private int spareLimit() {
        return Math.max(0, plugin.getConfig().getInt("arena-pool.spare-worlds", 2));
    }

    private static boolean asyncPaste() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    /** Pasting the same map over a kept world restores every block in the map's region, air included. */
    private boolean keepSpare(ArenaImpl arena, World world) {
        if (changedWorlds.remove(arena.id())) {
            return false; // blocks changed during the match: rebuild from the schematic next time
        }
        return arena.mapId().isPresent() && keepSpare(arena.gameId(), arena.mapId().get(), world);
    }

    private final java.util.Set<ArenaId> changedWorlds = ConcurrentHashMap.newKeySet();

    @Override
    public void markWorldChanged(ArenaId id) {
        changedWorlds.add(id);
    }

    private boolean keepSpare(GameId game, String mapId, World world) {
        if (spareLimit() <= 0) {
            return false;
        }
        ArenaWorldConfig cfg = gameRegistry.get(game).map(GameDefinition::arenaWorld).orElse(null);
        if (cfg == null) {
            return false;
        }
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        if (!world.getPlayers().isEmpty()) {
            return false; // someone is still inside (an observer): don't reuse
        }
        applyWorldConfig(world, cfg);
        spares.addLast(new Spare(world, game, mapId, cfg));
        while (spares.size() > spareLimit()) {
            destroyWorld(spares.removeFirst().world());
        }
        return true;
    }

    private World takeSpare(GameId game, String mapId, ArenaWorldConfig cfg) {
        for (java.util.Iterator<Spare> it = spares.iterator(); it.hasNext();) {
            Spare spare = it.next();
            if (spare.game().equals(game) && spare.mapId().equals(mapId) && spare.config().equals(cfg)
                    && Bukkit.getWorld(spare.world().getUID()) != null) {
                it.remove();
                return spare.world();
            }
        }
        return null;
    }

    public void destroySpares() {
        while (!spares.isEmpty()) {
            destroyWorld(spares.removeFirst().world());
        }
    }

    private void destroyWorld(World world) {
        Path folder = world.getWorldFolder().toPath();
        Bukkit.unloadWorld(world, false);
        try {
            deleteRecursive(folder);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete world folder " + folder, e);
        }
    }

    private static long ms(long from, long to) {
        return (to - from) / 1_000_000;
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

    /** Players waiting for an ALLOCATING arena's map to finish pasting: arena -> (player -> spectator). */
    private final Map<ArenaId, Map<UUID, Boolean>> pendingJoins = new ConcurrentHashMap<>();

    public int pendingJoins(ArenaId id) {
        Map<UUID, Boolean> pending = pendingJoins.get(id);
        return pending == null ? 0 : pending.size();
    }

    private void admitPending(ArenaImpl arena) {
        Map<UUID, Boolean> pending = pendingJoins.remove(arena.id());
        if (pending == null) {
            return;
        }
        pending.forEach((uuid, spectator) -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !playerArena.containsKey(uuid)) {
                addPlayer(arena.id(), p, spectator);
            }
        });
    }

    private boolean queueWhilePasting(ArenaImpl arena, ArenaId id, Player player, boolean asSpectator) {
        Map<UUID, Boolean> pending = pendingJoins.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        if (!asSpectator && arena.players().size() + pending.size() >= arena.maxPlayers())
            return false;
        pending.put(player.getUniqueId(), asSpectator);
        return true;
    }

    @Override
    public boolean addPlayer(ArenaId id, Player player, boolean asSpectator) {
        ArenaImpl arena = arenas.get(id);
        if (arena == null)
            return false;
        if (arena.state() == ArenaState.ALLOCATING) {
            return queueWhilePasting(arena, id, player, asSpectator);
        }
        return addNow(arena, id, player, asSpectator);
    }

    private boolean addNow(ArenaImpl arena, ArenaId id, Player player, boolean asSpectator) {
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

    /** Fires a player leave event (so games drop them from teams and scores), then a spectator join event. */
    public boolean demoteToSpectator(ArenaId id, Player player) {
        ArenaImpl arena = arenas.get(id);
        if (arena == null || !id.equals(playerArena.get(player.getUniqueId()))
                || !arena.players().contains(player.getUniqueId())) {
            return false;
        }
        arena.removePlayer(player.getUniqueId());
        Bukkit.getPluginManager().callEvent(new ArenaPlayerLeaveEvent(arena, player,
                ArenaPlayerLeaveEvent.LeaveReason.KICK));
        arena.addSpectator(player.getUniqueId());
        teleportIn(arena, player, true);
        Bukkit.getPluginManager().callEvent(new ArenaPlayerJoinEvent(arena, player, true));
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
        // Only a LIVE match can end. LifecycleTicker handles COUNTDOWN -> WAITING aborts,
        // and a stray endMatch() from a game's leave handler must not crown a winner.
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

    void transition(ArenaImpl arena, ArenaState to, int phaseSeconds) {
        ArenaState from = arena.state();
        arena.setState(to);
        arena.setPhaseSecondsRemaining(phaseSeconds);
        if (to == ArenaState.STARTING || to == ArenaState.LIVE) {
            bringIntoArena(arena);
        }
        Bukkit.getPluginManager().callEvent(new ArenaStateChangeEvent(arena, from, to));
    }

    private MapDefinition pickMap(GameDefinition def, @Nullable String forcedMapId) {
        if (forcedMapId != null) {
            return mapService.get(def.id(), forcedMapId).orElseThrow(
                    () -> new IllegalArgumentException("Map not registered for " + def.id() + ": " + forcedMapId));
        }
        Hosting.MapChoice choice = hosting == null ? new Hosting.MapChoice(Hosting.Mode.DEFAULT, null)
                : hosting.mapChoice(def.id());
        if (choice.mode() == Hosting.Mode.FIXED) {
            MapDefinition fixedHere = mapService.get(def.id(), choice.mapId()).orElse(null);
            if (fixedHere != null) {
                return fixedHere;
            }
            logger.warning("hosting map " + choice.mapId() + " doesn't exist for " + def.id().value()
                    + "; picking a random map.");
        }
        MapSelectionPolicy policy = def.mapSelection();
        if (choice.mode() == Hosting.Mode.DEFAULT && policy instanceof MapSelectionPolicy.Fixed fixed) {
            return mapService.get(def.id(), fixed.mapId())
                    .orElseThrow(() -> new IllegalStateException("Fixed map missing: " + fixed.mapId()));
        }
        List<MapDefinition> candidates = List.copyOf(mapService.forGame(def.id()));
        if (candidates.isEmpty())
            return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private World createVoidWorld(ArenaId arenaId, ArenaWorldConfig worldConfig) {
        return createVoidWorld("tea_arena_" + arenaId.value().toString().replace('-', '_'), worldConfig);
    }

    private int switchSeq;

    /** {@code done} runs on the main thread once players are on the new map, or right away if the arena is gone. */
    public void switchMap(ArenaId id, String newMapId, Runnable done) {
        ArenaImpl arena = arenas.get(id);
        GameDefinition def = arena == null ? null : gameRegistry.get(arena.gameId()).orElse(null);
        MapDefinition mapDef = def == null ? null : mapService.get(arena.gameId(), newMapId).orElse(null);
        if (arena == null || mapDef == null) {
            done.run();
            return;
        }
        long t0 = System.nanoTime();
        World spare = takeSpare(arena.gameId(), newMapId, def.arenaWorld());
        World fresh = spare != null ? spare
                : createVoidWorld("tea_arena_" + id.value().toString().replace('-', '_') + "_" + (++switchSeq),
                        def.arenaWorld());
        WaitingRooms.Room room = waitingRooms.roomFor(arena.gameId(), newMapId).orElse(null);
        Runnable finish = () -> {
            if (arenas.get(id) != arena) {
                destroyWorld(fresh);
                done.run();
                return;
            }
            World old = arena.world().orElse(null);
            String oldMap = arena.mapId().orElse(null);
            arena.setWorld(fresh);
            arena.setMapId(newMapId);
            arena.setWaitingRoom(room);
            Location waiting = resolveWaitingSpawn(arena).orElse(null);
            Location inMap = arenaSpawn(arena).orElse(null);
            for (UUID uuid : arena.allOccupants()) {
                Player p = Bukkit.getPlayer(uuid);
                // Players in a shared waiting area outside the arena stay put; players in
                // the old world (map or waiting room) follow the arena to the new one.
                if (p == null || (old != null && !p.getWorld().equals(old))) {
                    continue;
                }
                Location to = arena.spectators().contains(uuid) ? inMap : waiting;
                if (to != null) {
                    p.teleport(to);
                }
            }
            if (old != null && (oldMap == null || !keepSpare(arena.gameId(), oldMap, old))) {
                destroyWorld(old);
            }
            logger.info("Arena " + id + " switched to map " + newMapId + " (" + ms(t0, System.nanoTime()) + " ms)");
            done.run();
        };
        if (spare != null) {
            if (room != null) {
                waitingRooms.paste(room, fresh);
            }
            finish.run();
        } else if (asyncPaste()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                mapService.paste(mapDef, fresh);
                if (room != null) {
                    waitingRooms.paste(room, fresh);
                }
                Bukkit.getScheduler().runTask(plugin, finish);
            });
        } else {
            mapService.paste(mapDef, fresh);
            if (room != null) {
                waitingRooms.paste(room, fresh);
            }
            finish.run();
        }
    }

    private World createVoidWorld(String worldName, ArenaWorldConfig worldConfig) {
        WorldCreator creator = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().worlds().voidWorld(worldName)
                .environment(World.Environment.NORMAL)
                .generateStructures(false);
        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create arena world " + worldName);
        }
        world.setSpawnLocation(0, 64, 0);
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);

        // Paper reads data/raids.dat on unload even with autoSave off and logs a
        // NoSuchFileException when it's missing. An empty file makes the save a no-op.
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

        applyWorldConfig(world, worldConfig);
        return world;
    }

    private static void applyWorldConfig(World world, ArenaWorldConfig worldConfig) {
        world.setDifficulty(worldConfig.difficulty());
        world.setSpawnFlags(worldConfig.allowMonsters(), worldConfig.allowAnimals());
        for (var entry : worldConfig.gameRules().entrySet()) {
            applyGameRule(world, entry.getKey(), entry.getValue());
        }
        if (worldConfig.timeOfDay() != null) {
            world.setTime(worldConfig.timeOfDay());
        }
        world.setStorm(worldConfig.storm() != null && worldConfig.storm());
        world.setThundering(worldConfig.thundering() != null && worldConfig.thundering());
    }

    private static void applyGameRule(World world, String rule, String value) {
        me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().worlds().setGameRule(world, rule, value);
    }

    private void bringIntoArena(ArenaImpl arena) {
        World world = arena.world().orElse(null);
        Location spawn = arenaSpawn(arena).orElse(null);
        if (world == null || spawn == null) {
            return;
        }
        WaitingRooms.Room room = arena.waitingRoom();
        for (UUID uuid : arena.players()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && (!p.getWorld().equals(world) || (room != null && room.contains(p.getLocation())))) {
                p.teleport(spawn);
            }
        }
    }

    private void teleportIn(ArenaImpl arena, Player player, boolean asSpectator) {
        // Spectators watch the arena itself; players wait in the waiting area if there is one.
        Optional<Location> target = asSpectator ? arenaSpawn(arena) : resolveWaitingSpawn(arena);
        if (target.isEmpty()) return;
        player.teleport(target.get());
        ArenaWorldConfig cfg = gameRegistry.get(arena.gameId())
                .map(GameDefinition::arenaWorld)
                .orElseGet(() -> ArenaWorldConfig.defaults().build());
        player.setGameMode(asSpectator ? cfg.spectatorGameMode() : cfg.playerGameMode());
    }

    /** Also used by the pre-spawn hook so a cross-server-routed player spawns straight in the waiting room instead of the overworld. */
    public Optional<Location> resolveWaitingSpawn(ArenaId id) {
        ArenaImpl arena = arenas.get(id);
        return arena == null ? Optional.empty() : resolveWaitingSpawn(arena);
    }

    private Optional<Location> resolveWaitingSpawn(ArenaImpl arena) {
        ArenaState state = arena.state();
        boolean waiting = state == ArenaState.WAITING || state == ArenaState.COUNTDOWN;
        if (waiting && arena.world().isPresent() && arena.waitingRoom() != null) {
            return Optional.of(arena.waitingRoom().spawnIn(arena.world().get()));
        }
        if (waiting && arena.world().isPresent()) {
            Optional<Location> shared = waitingAreas.forGame(arena.gameId());
            if (shared.isPresent()) {
                return shared;
            }
        }
        return arenaSpawn(arena);
    }

    private Optional<Location> arenaSpawn(ArenaImpl arena) {
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
        // Behind a proxy, send the player back to the lobby server instead of stranding them here.
        try {
            me.playbosswar.tea.core.api.TeaCoreAPI core = me.playbosswar.tea.core.api.TeaCoreAPI.get();
            if (!core.isStandalone()) {
                String lobby = core.lobbyServerName();
                if (lobby != null && !lobby.isBlank()) {
                    // The hop takes a moment and the player is no longer in an
                    // arena, so no game protects them: spectator until they're
                    // gone (game mode doesn't carry across servers).
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    core.proxyTransport().connect(player, lobby);
                    return;
                }
            }
        } catch (IllegalStateException ignored) {
            // API not initialized yet: fall through to a local teleport.
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

    ArenaImpl arenaImpl(ArenaId id) {
        return arenas.get(id);
    }

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
