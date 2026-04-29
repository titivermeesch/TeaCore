package me.playbosswar.tea.core.impl.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * Drains queue entries into matching arenas. Every server runs its own
 * matchmaker tick — each one only handles entries for players that are
 * currently online on its instance, so no leader election is needed and
 * dequeues never race. Routing into a remote arena is done via the
 * {@code BungeeCord:Connect} plugin-message channel from the player's local
 * server.
 *
 * <p>For each queued player local to this server:
 * <ol>
 * <li>Find an arena with available capacity and matching map filter
 * (cluster-wide via {@link CoordinationBackend#allArenas()}).</li>
 * <li>If on the same server as the arena, directly
 * {@link ArenaManager#addPlayer}.</li>
 * <li>Otherwise, send a {@code BungeeCord:Connect} message to move the
 * player.</li>
 * </ol>
 */
public final class Matchmaker implements Listener {

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final GameRegistry games;
    private final ArenaManagerImpl localArenas;
    private final ProxyTransport transport;
    /**
     * Players we've recently sent {@code BungeeCord:Connect} for, mapped to
     * the moment we sent it. The disconnect listener checks this before
     * dequeueing on quit so a legitimate proxy hop doesn't lose its queue
     * entry. Entries older than the grace window are treated as expired
     * (handled in {@link #wasRecentlyRouted}).
     */
    private final ConcurrentHashMap<UUID, Instant> recentlyRouted = new ConcurrentHashMap<>();
    private BukkitTask task;

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
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * Whether this server's matchmaker has issued a {@code BungeeCord:Connect}
     * for the given player within the supplied grace window. Used by the
     * disconnect listener to recognize a proxy hop and skip the queue dequeue
     * — without this, a player getting routed to another server has their
     * entry erased the moment they leave the lobby, and the destination
     * server has nothing to act on when they arrive.
     *
     * <p>
     * Looking up an expired entry has a side effect: the entry is removed.
     * That keeps the map bounded; nothing else cleans it up.
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

    private void tick() {
        // Collect the union of locally-registered games and games appearing in
        // any cluster-wide arena snapshot. The lobby usually has no game
        // plugin installed, but it still hosts queued players for those games,
        // so we have to look at the fleet-wide arena set to know which queues
        // to walk.
        Set<GameId> gameIds = new HashSet<>();
        games.all().forEach(def -> gameIds.add(def.id()));
        for (ArenaSnapshot snap : backend.allArenas()) gameIds.add(snap.gameId());

        for (GameId gameId : gameIds) {
            var queue = backend.queueFor(gameId);
            if (queue.isEmpty()) continue;

            plugin.getLogger().info("[mm] " + gameId.value() + ": " + queue.size() + " queued");
            for (QueueEntry entry : queue) {
                tryRoute(entry);
            }
        }
    }

    /**
     * Pre-spawn hook: fires before Bukkit places the player in any world,
     * so we can override the spawn location to land directly inside the
     * arena's waiting room. Without this, a cross-server-routed player
     * materializes at the destination server's default world spawn
     * (overworld) for a frame before {@link #onPlayerJoin} schedules a
     * teleport into the arena — that frame is the visible flicker.
     *
     * <p>Only acts when the player has a queue entry AND the matchmaker
     * picks a LOCAL arena. Remote routing is handled the usual way through
     * {@link #onPlayerJoin}; for those we don't fight Bukkit over the
     * spawn location.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreSpawn(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        QueueEntry entry = backend.queueEntryOf(player.getUniqueId()).orElse(null);
        if (entry == null) return;
        Optional<ArenaSnapshot> picked = pickArena(entry);
        if (picked.isEmpty()) return;
        ArenaSnapshot snapshot = picked.get();
        if (!snapshot.serverId().equals(backend.serverId())) return;
        Optional<Location> spawn = localArenas.resolveWaitingSpawn(snapshot.arenaId());
        if (spawn.isEmpty()) return;
        event.setSpawnLocation(spawn.get());
    }

    /**
     * Player-just-joined-this-server hook. The proxy hop ends with a
     * {@link PlayerJoinEvent}; if the player has a queue entry waiting, route
     * them on the next tick (so Bukkit finishes initializing them) instead of
     * making them wait up to a full second for the next matchmaker tick.
     *
     * <p>Combined with {@link #onPreSpawn}, the player materializes at the
     * arena's waiting spawn directly, then 1 tick later this handler fires
     * {@code addPlayer} so they get registered in arena state and any
     * per-game starter items / scoreboard / etc. attach.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            backend.queueEntryOf(player.getUniqueId()).ifPresent(this::tryRoute);
        }, 1L);
    }

    /**
     * Process a single queue entry: skip if the player isn't local, dequeue
     * if they're already in an arena, otherwise pick a target arena and
     * either route them locally (addPlayer) or via BungeeCord:Connect to a
     * remote server.
     */
    private void tryRoute(QueueEntry entry) {
        Player player = Bukkit.getPlayer(entry.playerUuid());
        if (player == null) {
            plugin.getLogger().info("[mm]   " + entry.playerName() + "/" + entry.playerUuid()
                    + " not local on this server (server-id=" + backend.serverId() + "); skipping");
            return;
        }
        if (localArenas.arenaOf(player.getUniqueId()).isPresent()) {
            plugin.getLogger().info("[mm]   " + player.getName()
                    + " is already in a local arena; dequeueing");
            backend.dequeue(entry.playerUuid());
            return;
        }

        Optional<ArenaSnapshot> target = pickArena(entry);
        if (target.isEmpty()) {
            long total = backend.allArenas().stream()
                    .filter(s -> s.gameId().equals(entry.gameId())).count();
            plugin.getLogger().info("[mm]   no available arena for " + player.getName()
                    + " (game=" + entry.gameId().value() + ", mapFilter=" + entry.mapIds()
                    + "); total snapshots for this game=" + total
                    + " — see snapshot dump below");
            backend.allArenas().stream()
                    .filter(s -> s.gameId().equals(entry.gameId()))
                    .forEach(s -> plugin.getLogger().info("[mm]     - arena "
                            + s.arenaId().value().toString().substring(0, 8)
                            + " server=" + s.serverId()
                            + " state=" + s.state()
                            + " players=" + s.players() + "/" + s.maxPlayers()
                            + " map=" + s.mapId()
                            + " available=" + s.isAvailable()));
            return;
        }

        ArenaSnapshot snapshot = target.get();
        if (snapshot.serverId().equals(backend.serverId())) {
            plugin.getLogger().info("[mm]   routing " + player.getName()
                    + " to LOCAL arena " + snapshot.arenaId().value().toString().substring(0, 8));
            localArenas.get(snapshot.arenaId()).ifPresent(arena -> {
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
                    + ") via BungeeCord:Connect");
            // Mark as in-flight BEFORE sending the Connect, since the
            // PlayerQuitEvent fires shortly after as the proxy moves them off
            // this server. The disconnect listener checks this map and skips
            // the dequeue. Don't dequeue here either — the destination
            // server will dequeue when it processes the join.
            recentlyRouted.put(player.getUniqueId(), Instant.now());
            transport.connect(player, snapshot.serverId());
        }
    }

    private Optional<ArenaSnapshot> pickArena(QueueEntry entry) {
        ArenaSnapshot best = null;
        int bestPlayers = -1;
        for (ArenaSnapshot snapshot : backend.allArenas()) {
            if (!snapshot.gameId().equals(entry.gameId()))
                continue;
            if (!snapshot.isAvailable())
                continue;
            if (!entry.acceptsAnyMap() && !entry.mapIds().contains(snapshot.mapId()))
                continue;
            if (snapshot.players() > bestPlayers) {
                best = snapshot;
                bestPlayers = snapshot.players();
            }
        }
        return Optional.ofNullable(best);
    }
}
