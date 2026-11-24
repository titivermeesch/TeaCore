package me.playbosswar.tea.core.impl.arena;

import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.game.PoolConfig;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.impl.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * STANDALONE allocates locally with per-server counts. CENTRAL works only on the
 * matchmaker leader, with network-wide counts, and sends ALLOCATE_ARENA to the
 * least-loaded server. EXTERNAL does nothing here (one arena per pod, scaled externally).
 */
public final class ArenaPoolManager {

    private final Plugin plugin;
    private final ArenaManagerImpl arenaManager;
    private final GameRegistry gameRegistry;
    private final MapService mapService;
    private final CoordinationBackend coordination;
    private final CoreConfig.ArenaPoolConfig poolConfig;
    private final boolean eligibleMatchmaker;
    private final Logger logger;
    private final Set<String> loggedNoMaps = ConcurrentHashMap.newKeySet();
    // Last allocate attempt per game, so the leader respects scale-cooldown-seconds
    // when a target is slow or can't allocate.
    private final java.util.concurrent.ConcurrentHashMap<GameId, Long> lastAllocateAttemptMs =
            new java.util.concurrent.ConcurrentHashMap<>();
    private BukkitTask task;
    private Hosting hosting;
    /** External mode: when the open arena was first seen empty while another game had a queue. */
    private long idleSince;

    public ArenaPoolManager(Plugin plugin, ArenaManagerImpl arenaManager, GameRegistry gameRegistry,
            MapService mapService, CoordinationBackend coordination,
            CoreConfig.ArenaPoolConfig poolConfig, boolean eligibleMatchmaker, Logger logger) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.gameRegistry = gameRegistry;
        this.mapService = mapService;
        this.coordination = coordination;
        this.poolConfig = poolConfig;
        this.eligibleMatchmaker = eligibleMatchmaker;
        this.logger = logger;
    }

    public void setHosting(Hosting hosting) {
        this.hosting = hosting;
    }

    private boolean hosted(GameId game) {
        return hosting == null || hosting.hosts(game);
    }

    public void start() {
        if (task != null) return;
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.EXTERNAL) {
            logger.info("Arena pool mode=external: this server runs one match at a time;"
                    + " your orchestrator scales by adding servers.");
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::keepExternalArena, 40L, 40L);
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void preload(GameDefinition def) {
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.EXTERNAL) return; // see keepExternalArena
        // In central mode the leader handles preload through the normal tick;
        // individual backends should not preload locally because that double-counts.
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) return;
        if (!hosted(def.id())) return;
        PoolConfig effective = effectivePool(def.id());
        int target = Math.max(0, effective.preloadOnStartup());
        for (int i = 0; i < target; i++) {
            safeAllocate(def.id());
        }
    }

    private void tick() {
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
            // In central mode only the lobby sizes the pool. Arena servers opt out with
            // network.eligible-matchmaker: false so the lobby is the only one competing for the leader lock.
            if (!eligibleMatchmaker) return;
            if (!coordination.tryAcquireMatchmakerLock()) return;
        }

        for (GameId gameId : gameIdsToConsider()) {
            PoolConfig effective = effectivePool(gameId);
            if (effective.maxTotal() <= 0) continue;

            int available;
            int total;
            int freeSlots;
            if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
                var snaps = coordination.allArenas().stream()
                        .filter(s -> s.gameId().equals(gameId))
                        .toList();
                available = (int) snaps.stream().filter(ArenaPoolManager::isAvailable).count();
                freeSlots = snaps.stream().filter(ArenaPoolManager::isAvailable)
                        .mapToInt(s -> Math.max(0, s.maxPlayers() - s.players())).sum();
                total = (int) snaps.stream().filter(s -> s.state() != ArenaState.RELEASED).count();
            } else {
                var local = arenaManager.all().stream()
                        .filter(a -> a.gameId().equals(gameId))
                        .toList();
                available = (int) local.stream().filter(ArenaPoolManager::isAvailable).count();
                freeSlots = local.stream().filter(ArenaPoolManager::isAvailable)
                        .mapToInt(a -> Math.max(0, a.maxPlayers() - a.players().size())).sum();
                total = (int) local.stream().filter(a -> a.state() != ArenaState.RELEASED).count();
            }
            // More players queued than the joinable arenas can take: open another
            // arena even though min-available is met.
            int queued = coordination.queueFor(gameId).size();
            if (queued > freeSlots) {
                available = Math.min(available, effective.minAvailable() - 1);
            }
            if (available < effective.minAvailable() && total < effective.maxTotal()) {
                long now = System.currentTimeMillis();
                long cooldownMs = Math.max(1, effective.scaleCooldownSeconds()) * 1000L;
                Long last = lastAllocateAttemptMs.get(gameId);
                if (last != null && now - last < cooldownMs) continue;
                lastAllocateAttemptMs.put(gameId, now);
                safeAllocate(gameId);
            } else if (queued == 0 && available > effective.minAvailable()) {
                scaleDown(gameId, available - effective.minAvailable());
            }
        }
        emptySince.keySet().removeIf(id -> !seenEmpty.contains(id));
        seenEmpty.clear();
    }

    /**
     * External mode. With one hosted game, keeps {@code preload-on-startup} arenas
     * of it open (default 1). With several, keeps one arena open for the game the
     * most players are queued for, and swaps it for another game when it sits
     * empty while only that other game has a queue.
     */
    private void keepExternalArena() {
        List<GameId> hosted = new ArrayList<>();
        for (GameDefinition def : gameRegistry.all()) {
            if (hosted(def.id()) && !mapService.forGame(def.id()).isEmpty()) {
                hosted.add(def.id());
            }
        }
        if (hosted.size() == 1) {
            GameId only = hosted.get(0);
            CoreConfig.GamePoolOverride override = poolConfig.games().get(only.value());
            int target = override != null && override.preloadOnStartup() > 0 ? override.preloadOnStartup() : 1;
            long open = arenaManager.all().stream()
                    .filter(a -> a.gameId().equals(only) && a.state() != ArenaState.RELEASED).count();
            if (open < target) {
                safeAllocate(only);
            }
            return;
        }
        List<Arena> open = arenaManager.all().stream().filter(a -> a.state() != ArenaState.RELEASED).toList();
        if (open.isEmpty()) {
            idleSince = 0;
            GameId game = Hosting.pick(hosted, g -> coordination.queueFor(g).size(), null);
            if (game != null) {
                logger.info("Opening a " + game.value() + " arena (" + coordination.queueFor(game).size()
                        + " queued).");
                safeAllocate(game);
            }
            return;
        }
        if (open.size() != 1) {
            return;
        }
        Arena arena = open.get(0);
        boolean empty = arena.state() == ArenaState.WAITING && arena.players().isEmpty()
                && arena.spectators().isEmpty();
        GameId wanted = Hosting.pick(hosted, g -> coordination.queueFor(g).size(), arena.gameId());
        if (!empty || wanted == null || wanted.equals(arena.gameId())
                || coordination.queueFor(arena.gameId()).size() > 0) {
            idleSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (idleSince == 0) {
            idleSince = now;
        } else if (now - idleSince >= 10_000) {
            idleSince = 0;
            logger.info("Switching from " + arena.gameId().value() + " to " + wanted.value()
                    + ": players are queued for it and this arena is empty.");
            arenaManager.release(arena.id());
        }
    }

    /** Arena id -> when the leader first saw it waiting with nobody in it. */
    private final Map<String, Long> emptySince = new HashMap<>();
    private final Set<String> seenEmpty = new java.util.HashSet<>();

    /** Releases arenas idle past {@code scale-down-idle-seconds}. Never below min-available, and only while nobody is queued. */
    private void scaleDown(GameId gameId, int surplus) {
        long idleMs = plugin.getConfig().getLong("arena-pool.scale-down-idle-seconds", 60) * 1000L;
        if (idleMs <= 0 || surplus <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        record Empty(String arenaId, String serverId) { }
        List<Empty> empty = new java.util.ArrayList<>();
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
            for (ArenaSnapshot snap : coordination.allArenas()) {
                if (snap.gameId().equals(gameId) && snap.state() == ArenaState.WAITING
                        && snap.players() == 0 && snap.roster().isEmpty()) {
                    empty.add(new Empty(snap.arenaId().value().toString(), snap.serverId()));
                }
            }
        } else {
            for (Arena arena : arenaManager.all()) {
                if (arena.gameId().equals(gameId) && arena.state() == ArenaState.WAITING
                        && arena.players().isEmpty() && arena.spectators().isEmpty()) {
                    empty.add(new Empty(arena.id().value().toString(), null));
                }
            }
        }
        for (Empty e : empty) {
            seenEmpty.add(e.arenaId());
            long since = emptySince.computeIfAbsent(e.arenaId(), k -> now);
            if (surplus <= 0 || now - since < idleMs) {
                continue;
            }
            surplus--;
            emptySince.remove(e.arenaId());
            logger.info("Releasing idle " + gameId.value() + " arena " + e.arenaId()
                    + " (more empty arenas than arena-pool min-available).");
            if (e.serverId() != null) {
                coordination.publishControl(e.serverId(), ControlCommand.of(
                        ControlCommand.Type.RELEASE_ARENA, e.arenaId(), gameId.value(), null, null));
            } else {
                arenaManager.release(new me.playbosswar.tea.core.api.arena.ArenaId(java.util.UUID.fromString(e.arenaId())));
            }
        }
    }

    /** In CENTRAL mode this includes game ids from config and from every host, since the leader usually doesn't run the game plugins. */
    private Set<GameId> gameIdsToConsider() {
        Set<GameId> ids = new java.util.HashSet<>();
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
            poolConfig.games().keySet().forEach(s -> ids.add(GameId.of(s)));
            ids.addAll(coordination.allHostedGames());
        } else {
            for (GameDefinition def : gameRegistry.all()) {
                if (hosted(def.id())) ids.add(def.id());
            }
        }
        return ids;
    }

    private void safeAllocate(GameId gameId) {
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
            allocateViaControlCommand(gameId);
            return;
        }
        // Local allocation path: verify a map exists so we don't spam warnings
        // while the operator is still authoring maps via /tea map create.
        if (mapService.forGame(gameId).isEmpty()) {
            if (loggedNoMaps.add(gameId.value())) {
                logger.info("Skipping pool preload for " + gameId.value()
                        + " until at least one map is authored (/tea map create).");
            }
            return;
        }
        try {
            arenaManager.allocate(gameId);
            loggedNoMaps.remove(gameId.value());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Pool allocation failed for " + gameId, e);
        }
    }

    private void allocateViaControlCommand(GameId gameId) {
        Set<String> hosts = coordination.hostsOf(gameId);
        if (hosts.isEmpty()) {
            if (loggedNoMaps.add(gameId.value())) {
                logger.info("No backend has registered as a host for " + gameId.value()
                        + "; waiting for one to come online. (check plugin install + game registration)");
            }
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String host : hosts) counts.put(host, 0);
        for (ArenaSnapshot s : coordination.allArenas()) {
            if (!s.gameId().equals(gameId)) continue;
            if (s.state() == ArenaState.RELEASED) continue;
            counts.merge(s.serverId(), 1, Integer::sum);
        }
        var cloud = me.playbosswar.tea.core.impl.cloud.CloudIntegration.get();
        int cap = cloud == null ? Integer.MAX_VALUE : cloud.arenasPerService(gameId);
        String target = counts.entrySet().stream()
                .filter(e -> e.getValue() < cap)
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (target == null) return;
        coordination.publishControl(target, ControlCommand.of(
                ControlCommand.Type.ALLOCATE_ARENA,
                null, gameId.value(), null, null));
        loggedNoMaps.remove(gameId.value());
    }

    /** Local config override first (the source of truth in central mode), then the game's own pool config, else zero (no auto-allocation). */
    private PoolConfig effectivePool(GameId gameId) {
        Optional<GameDefinition> local = gameRegistry.get(gameId);
        PoolConfig baseline = local.map(GameDefinition::pool)
                .orElse(new PoolConfig(0, 0, 0, 5));
        CoreConfig.GamePoolOverride override = poolConfig.games().get(gameId.value());
        if (override == null) return baseline;
        int min = override.minAvailable() >= 0 ? override.minAvailable() : baseline.minAvailable();
        int max = override.maxTotal() >= 0 ? override.maxTotal() : baseline.maxTotal();
        int preload = override.preloadOnStartup() >= 0 ? override.preloadOnStartup() : baseline.preloadOnStartup();
        return new PoolConfig(min, max, preload, baseline.scaleCooldownSeconds());
    }

    /** A full arena counting down is not available, so the next arena opens during its countdown rather than after it starts. */
    private static boolean isAvailable(Arena arena) {
        return arena.state() == ArenaState.ALLOCATING
                || ((arena.state() == ArenaState.WAITING || arena.state() == ArenaState.COUNTDOWN)
                        && (arena.maxPlayers() <= 0 || arena.players().size() < arena.maxPlayers()));
    }

    private static boolean isAvailable(ArenaSnapshot snap) {
        return snap.state() == ArenaState.ALLOCATING
                || ((snap.state() == ArenaState.WAITING || snap.state() == ArenaState.COUNTDOWN)
                        && (snap.maxPlayers() <= 0 || snap.players() < snap.maxPlayers()));
    }

    /** Called on reload. */
    @SuppressWarnings("unused")
    public void clearSkippedLog() {
        loggedNoMaps.clear();
    }

    @SuppressWarnings("unused")
    private static Optional<ArenaSnapshot> firstOf(List<ArenaSnapshot> snaps) {
        return snaps.isEmpty() ? Optional.empty() : Optional.of(snaps.get(0));
    }
}
