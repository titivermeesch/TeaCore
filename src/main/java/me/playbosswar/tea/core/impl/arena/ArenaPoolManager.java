package me.playbosswar.tea.core.impl.arena;

import java.util.Comparator;
import java.util.HashMap;
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
 * Maintains the warm pool of arenas. Behavior depends on
 * {@link CoreConfig.ArenaPoolMode}:
 *
 * <ul>
 * <li>{@link CoreConfig.ArenaPoolMode#STANDALONE STANDALONE}: ticks on every
 * backend, allocates locally against {@link ArenaManagerImpl}. Counts are
 * per-backend.</li>
 * <li>{@link CoreConfig.ArenaPoolMode#CENTRAL CENTRAL}: only the matchmaker
 * leader actually does work. Counts are network-wide
 * ({@link CoordinationBackend#allArenas()}); allocations fan out to the
 * least-loaded backend via {@link ControlCommand.Type#ALLOCATE_ARENA}. On
 * every other backend {@code tick()} is a no-op.</li>
 * <li>{@link CoreConfig.ArenaPoolMode#EXTERNAL EXTERNAL}: no-op on every
 * backend - assumes one arena per pod with k8s/HPA-style external
 * scaling.</li>
 * </ul>
 *
 * <p>
 * Per-game pool sizing comes from {@link CoreConfig.GamePoolOverride} if set,
 * otherwise the game's {@link PoolConfig} default.
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
    // Per-game "last allocate attempt" stamp so the leader respects
    // scale-cooldown-seconds and doesn't spam allocate when a target is slow
    // or unable to fulfill a command.
    private final java.util.concurrent.ConcurrentHashMap<GameId, Long> lastAllocateAttemptMs =
            new java.util.concurrent.ConcurrentHashMap<>();
    private BukkitTask task;

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

    public void start() {
        if (task != null) return;
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.EXTERNAL) {
            logger.info("Arena pool mode=external; pool manager is a no-op. External orchestration owns scaling.");
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

    /** Applies {@code preload-on-startup} for this game at registration time. */
    public void preload(GameDefinition def) {
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.EXTERNAL) return;
        // In central mode the leader handles preload through the normal tick;
        // individual backends should not preload locally because that double-counts.
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) return;
        PoolConfig effective = effectivePool(def.id());
        int target = Math.max(0, effective.preloadOnStartup());
        for (int i = 0; i < target; i++) {
            safeAllocate(def.id());
        }
    }

    private void tick() {
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
            // In central mode the lobby is the single source of truth for pool
            // sizing. Arena backends explicitly opt out via
            // network.eligible-matchmaker: false so the lobby is the only
            // server competing for the leader lock.
            if (!eligibleMatchmaker) return;
            if (!coordination.tryAcquireMatchmakerLock()) return;
        }

        for (GameId gameId : gameIdsToConsider()) {
            PoolConfig effective = effectivePool(gameId);
            if (effective.maxTotal() <= 0) continue; // no sizing declared; skip

            int available;
            int total;
            if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
                // Network-wide counts across every hosting backend.
                var snaps = coordination.allArenas();
                available = (int) snaps.stream()
                        .filter(s -> s.gameId().equals(gameId))
                        .filter(ArenaPoolManager::isAvailable)
                        .count();
                total = (int) snaps.stream()
                        .filter(s -> s.gameId().equals(gameId))
                        .filter(s -> s.state() != ArenaState.RELEASED)
                        .count();
            } else {
                available = (int) arenaManager.all().stream()
                        .filter(a -> a.gameId().equals(gameId))
                        .filter(ArenaPoolManager::isAvailable)
                        .count();
                total = (int) arenaManager.all().stream()
                        .filter(a -> a.gameId().equals(gameId))
                        .filter(a -> a.state() != ArenaState.RELEASED)
                        .count();
            }
            if (available < effective.minAvailable() && total < effective.maxTotal()) {
                long now = System.currentTimeMillis();
                long cooldownMs = Math.max(1, effective.scaleCooldownSeconds()) * 1000L;
                Long last = lastAllocateAttemptMs.get(gameId);
                if (last != null && now - last < cooldownMs) continue;
                lastAllocateAttemptMs.put(gameId, now);
                safeAllocate(gameId);
            }
        }
    }

    /**
     * Game ids the pool manager should consider this tick.
     *
     * <ul>
     * <li>CENTRAL: union of every game id appearing in this server's
     *     {@code arena-pool.games} config and every game id any host in the
     *     fleet has published. The local {@link GameRegistry} is not consulted
     *     here — the leader (typically the lobby) doesn't run the game plugins
     *     itself.</li>
     * <li>STANDALONE: just the locally registered games, since this single
     *     backend owns everything.</li>
     * </ul>
     */
    private Set<GameId> gameIdsToConsider() {
        Set<GameId> ids = new java.util.HashSet<>();
        if (poolConfig.mode() == CoreConfig.ArenaPoolMode.CENTRAL) {
            poolConfig.games().keySet().forEach(s -> ids.add(GameId.of(s)));
            ids.addAll(coordination.allHostedGames());
        } else {
            for (GameDefinition def : gameRegistry.all()) ids.add(def.id());
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
        // Pick the host with the fewest existing arenas for this game.
        Map<String, Integer> counts = new HashMap<>();
        for (String host : hosts) counts.put(host, 0);
        for (ArenaSnapshot s : coordination.allArenas()) {
            if (!s.gameId().equals(gameId)) continue;
            if (s.state() == ArenaState.RELEASED) continue;
            counts.merge(s.serverId(), 1, Integer::sum);
        }
        String target = counts.entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (target == null) return;
        coordination.publishControl(target, new ControlCommand(
                ControlCommand.Type.ALLOCATE_ARENA,
                null, gameId.value(), null, null));
        loggedNoMaps.remove(gameId.value());
    }

    /**
     * Effective pool sizing for a game. Looks up the local config override
     * first (the single source of truth in central mode); falls back to the
     * locally registered {@link GameDefinition#pool()} if available, otherwise
     * to a zero-sized config that means "do not auto-allocate."
     */
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

    private static boolean isAvailable(Arena arena) {
        return arena.state() == ArenaState.WAITING || arena.state() == ArenaState.COUNTDOWN;
    }

    private static boolean isAvailable(ArenaSnapshot snap) {
        return snap.state() == ArenaState.WAITING || snap.state() == ArenaState.COUNTDOWN;
    }

    /** Used by external callers (reload) to drop the "skipped" warning cache. */
    @SuppressWarnings("unused")
    public void clearSkippedLog() {
        loggedNoMaps.clear();
    }

    @SuppressWarnings("unused")
    private static Optional<ArenaSnapshot> firstOf(List<ArenaSnapshot> snaps) {
        return snaps.isEmpty() ? Optional.empty() : Optional.of(snaps.get(0));
    }
}
