package me.playbosswar.tea.core.impl.coordination;

import java.util.Optional;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Drains queue entries into matching arenas. Runs every second on the leader
 * instance (determined by
 * {@link CoordinationBackend#tryAcquireMatchmakerLock()}).
 * For each queued player:
 * <ol>
 * <li>Find an arena with available capacity and matching map filter.</li>
 * <li>If on the same server as the arena, directly
 * {@link ArenaManager#addPlayer}.</li>
 * <li>Otherwise, send a {@code BungeeCord:Connect} message to move the
 * player.</li>
 * </ol>
 */
public final class Matchmaker {

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final GameRegistry games;
    private final ArenaManager localArenas;
    private final ProxyTransport transport;
    private BukkitTask task;

    public Matchmaker(Plugin plugin, CoordinationBackend backend, GameRegistry games,
            ArenaManager localArenas, ProxyTransport transport) {
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

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        backend.releaseMatchmakerLock();
    }

    private void tick() {
        if (!backend.tryAcquireMatchmakerLock())
            return;

        for (var def : games.all()) {
            for (QueueEntry entry : backend.queueFor(def.id())) {
                Player player = Bukkit.getPlayer(entry.playerUuid());
                if (player == null)
                    continue; // player on a different server - skip; their server will handle it
                if (localArenas.arenaOf(player.getUniqueId()).isPresent()) {
                    backend.dequeue(entry.playerUuid());
                    continue;
                }

                Optional<ArenaSnapshot> target = pickArena(entry);
                if (target.isEmpty())
                    continue;

                ArenaSnapshot snapshot = target.get();
                if (snapshot.serverId().equals(backend.serverId())) {
                    localArenas.get(snapshot.arenaId()).ifPresent(arena -> {
                        if (localArenas.addPlayer(arena.id(), player, false)) {
                            backend.dequeue(entry.playerUuid());
                        }
                    });
                } else {
                    transport.connect(player, snapshot.serverId());
                    backend.dequeue(entry.playerUuid());
                }
            }
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
