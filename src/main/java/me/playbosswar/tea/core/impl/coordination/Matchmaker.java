package me.playbosswar.tea.core.impl.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import me.playbosswar.tea.core.impl.arena.ArenaManagerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Every server runs its own tick and only handles entries for players online
 * on it, so no leader election is needed and dequeues never race.
 */
public final class Matchmaker implements Listener {

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final GameRegistry games;
    private final ArenaManagerImpl localArenas;
    private final ProxyTransport transport;
    // Checked by the disconnect listener so a proxy hop doesn't lose its queue entry.
    private final ConcurrentHashMap<UUID, Instant> recentlyRouted = new ConcurrentHashMap<>();
    private static final Duration STALE_ROUTE = Duration.ofSeconds(30);
    private volatile BukkitTask task;
    private final AtomicBoolean collecting = new AtomicBoolean();
    private volatile me.playbosswar.tea.core.impl.rating.RatingService ratings;

    public void setRatings(me.playbosswar.tea.core.impl.rating.RatingService ratings) {
        this.ratings = ratings;
    }

    public Matchmaker(Plugin plugin, CoordinationBackend backend, GameRegistry games,
            ArenaManagerImpl localArenas, ProxyTransport transport) {
        this.plugin = plugin;
        this.backend = backend;
        this.games = games;
        this.localArenas = localArenas;
        this.transport = transport;
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::collect, 20L, 20L);
    }

    /**
     * Looking up an expired entry removes it. Nothing else keeps the map bounded.
     */
    public boolean wasRecentlyRouted(UUID playerUuid, Duration grace) {
        if (playerUuid == null) return false;
        Instant when = recentlyRouted.get(playerUuid);
        if (when == null) return false;
        if (Duration.between(when, Instant.now()).compareTo(grace) > 0) {
            recentlyRouted.remove(playerUuid);
            return false;
        }
        return true;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Async: reads the arenas and queues once, then routes on the main thread. */
    private void collect() {
        if (!collecting.compareAndSet(false, true)) {
            return;
        }
        try {
            // The lobby usually has no game plugin installed but still holds queued
            // players, so also include games seen in cluster-wide arena snapshots.
            List<ArenaSnapshot> arenas = List.copyOf(backend.allArenas());
            Set<GameId> gameIds = new HashSet<>();
            games.all().forEach(def -> gameIds.add(def.id()));
            for (ArenaSnapshot snap : arenas) gameIds.add(snap.gameId());

            List<QueueEntry> queued = new ArrayList<>();
            for (GameId gameId : gameIds) {
                var queue = backend.queueFor(gameId);
                if (queue.isEmpty()) continue;
                plugin.getLogger().fine("[mm] " + gameId.value() + ": " + queue.size() + " queued");
                queued.addAll(queue);
            }
            if (!queued.isEmpty() && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> route(queued, arenas));
            }
        } finally {
            collecting.set(false);
        }
    }

    private void route(List<QueueEntry> queued, List<ArenaSnapshot> arenas) {
        if (task == null) return;
        Map<ArenaId, Integer> reserved = new HashMap<>();
        for (QueueEntry entry : queued) {
            tryRoute(entry, arenas, reserved);
        }
    }

    /**
     * Sets the spawn to the local arena's waiting room before Bukkit places the
     * player, otherwise a routed player shows at the world spawn for one frame.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreSpawn(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        QueueEntry entry = backend.queueEntryOf(player.getUniqueId()).orElse(null);
        if (entry == null) return;
        Optional<ArenaSnapshot> picked = pickArena(entry, List.copyOf(backend.allArenas()), Map.of());
        if (picked.isEmpty()) return;
        ArenaSnapshot snapshot = picked.get();
        if (!snapshot.serverId().equals(backend.serverId())) return;
        Optional<Location> spawn = localArenas.resolveWaitingSpawn(snapshot.arenaId());
        if (spawn.isEmpty()) return;
        event.setSpawnLocation(spawn.get());
    }

    /**
     * Routes on the next tick, once Bukkit has initialized the player, instead of
     * waiting for the next matchmaker tick.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            backend.queueEntryOf(player.getUniqueId()).ifPresent(entry ->
                    tryRoute(entry, List.copyOf(backend.allArenas()), new HashMap<>()));
        }, 1L);
    }

    /**
     * @param arenas   read once per tick and shared by every entry in it
     * @param reserved players routed to each arena earlier in the same tick, which {@code arenas} doesn't show yet
     */
    private void tryRoute(QueueEntry entry, List<ArenaSnapshot> arenas, Map<ArenaId, Integer> reserved) {
        Player player = Bukkit.getPlayer(entry.playerUuid());
        if (player == null) {
            // The destination server never picked the entry up (it dequeues on arrival),
            // so the player never arrived. Drop it, or it re-routes them on reconnect.
            Instant routedAt = recentlyRouted.get(entry.playerUuid());
            if (routedAt != null && Duration.between(routedAt, Instant.now()).compareTo(STALE_ROUTE) > 0) {
                recentlyRouted.remove(entry.playerUuid());
                backend.dequeue(entry.playerUuid());
                plugin.getLogger().info("[mm]   " + entry.playerName()
                        + " never arrived after being routed; dequeueing");
                return;
            }
            plugin.getLogger().fine("[mm]   " + entry.playerName() + "/" + entry.playerUuid()
                    + " not local on this server (server-id=" + backend.serverId() + "); skipping");
            return;
        }
        if (localArenas.arenaOf(player.getUniqueId()).isPresent()) {
            plugin.getLogger().info("[mm]   " + player.getName()
                    + " is already in a local arena; dequeueing");
            backend.dequeue(entry.playerUuid());
            return;
        }

        Optional<ArenaSnapshot> target = pickArena(entry, arenas, reserved);
        if (target.isEmpty()) {
            if (!plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
                return;
            }
            long total = arenas.stream()
                    .filter(s -> s.gameId().equals(entry.gameId())).count();
            plugin.getLogger().fine("[mm]   no available arena for " + player.getName()
                    + " (game=" + entry.gameId().value() + ", mapFilter=" + entry.mapIds()
                    + "); total snapshots for this game=" + total
                    + " — see snapshot dump below");
            arenas.stream()
                    .filter(s -> s.gameId().equals(entry.gameId()))
                    .forEach(s -> plugin.getLogger().fine("[mm]     - arena "
                            + s.arenaId().value().toString().substring(0, 8)
                            + " server=" + s.serverId()
                            + " state=" + s.state()
                            + " players=" + s.players() + "/" + s.maxPlayers()
                            + " map=" + s.mapId()
                            + " available=" + s.isAvailable()));
            return;
        }

        ArenaSnapshot snapshot = target.get();
        reserved.merge(snapshot.arenaId(), Math.max(1, entry.partySize()), Integer::sum);
        if (entry.partyId() != null) {
            partyArenas.put(entry.partyId(), java.util.Map.entry(snapshot.arenaId(), Instant.now()));
        }
        if (snapshot.serverId().equals(backend.serverId())) {
            plugin.getLogger().info("[mm]   routing " + player.getName()
                    + " to LOCAL arena " + snapshot.arenaId().value().toString().substring(0, 8));
            localArenas.get(snapshot.arenaId()).ifPresent(arena -> {
                if (ratings != null) {
                    ratings.intend(player.getUniqueId(), entry.ranked());
                }
                if (localArenas.addPlayer(arena.id(), player, false)) {
                    backend.dequeue(entry.playerUuid());
                } else {
                    plugin.getLogger().warning("[mm]   localArenas.addPlayer rejected "
                            + player.getName() + " on arena "
                            + snapshot.arenaId().value().toString().substring(0, 8));
                }
            });
        } else {
            plugin.getLogger().info("[mm]   routing " + player.getName()
                    + " to REMOTE server '" + snapshot.serverId() + "' (arena "
                    + snapshot.arenaId().value().toString().substring(0, 8)
                    + ") through the proxy");
            // Marked before sending Connect because PlayerQuitEvent follows shortly and the
            // disconnect listener checks this map. The destination server dequeues on join.
            recentlyRouted.put(player.getUniqueId(), Instant.now());
            transport.connect(player, snapshot.serverId());
        }
    }

    /** Where each party's first member went, so the rest follow (party id -> arena, time). */
    private final ConcurrentHashMap<String, java.util.Map.Entry<me.playbosswar.tea.core.api.arena.ArenaId, Instant>>
            partyArenas = new ConcurrentHashMap<>();

    private Optional<ArenaSnapshot> pickArena(QueueEntry entry, List<ArenaSnapshot> arenas,
                                              Map<ArenaId, Integer> reserved) {
        if (entry.partyId() != null) {
            var placed = partyArenas.get(entry.partyId());
            if (placed != null && Duration.between(placed.getValue(), Instant.now()).toSeconds() < 30) {
                for (ArenaSnapshot snapshot : arenas) {
                    if (snapshot.arenaId().equals(placed.getKey()) && snapshot.isAvailable()) {
                        return Optional.of(snapshot);
                    }
                }
            }
        }
        int room = Math.max(1, entry.partySize());
        ArenaSnapshot best = null;
        int bestPlayers = -1;
        for (ArenaSnapshot snapshot : arenas) {
            int players = snapshot.players() + reserved.getOrDefault(snapshot.arenaId(), 0);
            if (players + room > snapshot.maxPlayers())
                continue;
            if (!snapshot.gameId().equals(entry.gameId()))
                continue;
            if (!snapshot.isAvailable())
                continue;
            if (!entry.acceptsAnyMap() && !entry.mapIds().contains(snapshot.mapId()))
                continue;
            if (!snapshot.acceptsRanked(entry.ranked(), entry.rating()))
                continue;
            if (players > bestPlayers) {
                best = snapshot;
                bestPlayers = players;
            }
        }
        return Optional.ofNullable(best);
    }
}
