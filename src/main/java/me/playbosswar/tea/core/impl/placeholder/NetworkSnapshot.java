package me.playbosswar.tea.core.impl.placeholder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.GameRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Arenas and queues of the whole network, read once a second off the main
 * thread so placeholders never wait on Redis.
 */
public final class NetworkSnapshot {

    public record QueuePlace(GameId game, int position, int size) { }

    private record View(List<ArenaSnapshot> arenas, Map<GameId, Integer> queueSizes, Map<UUID, QueuePlace> places) { }

    private final Plugin plugin;
    private final Supplier<CoordinationBackend> backend;
    private final GameRegistry games;
    private final Logger logger;
    private volatile View view = new View(List.of(), Map.of(), Map.of());
    private BukkitTask task;

    public NetworkSnapshot(Plugin plugin, Supplier<CoordinationBackend> backend, GameRegistry games, Logger logger) {
        this.plugin = plugin;
        this.backend = backend;
        this.games = games;
        this.logger = logger;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 0L, 20L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public List<ArenaSnapshot> arenas() {
        return view.arenas();
    }

    public int queueSize(GameId game) {
        return view.queueSizes().getOrDefault(game, 0);
    }

    public QueuePlace placeOf(UUID player) {
        return view.places().get(player);
    }

    private void refresh() {
        CoordinationBackend backend = this.backend.get();
        if (backend == null) {
            return;
        }
        try {
            List<ArenaSnapshot> arenas = new ArrayList<>(backend.allArenas());
            Set<GameId> gameIds = new HashSet<>();
            games.all().forEach(def -> gameIds.add(def.id()));
            arenas.forEach(a -> gameIds.add(a.gameId()));
            Map<GameId, Integer> sizes = new HashMap<>();
            Map<UUID, QueuePlace> places = new HashMap<>();
            for (GameId game : gameIds) {
                Collection<QueueEntry> queue = backend.queueFor(game);
                sizes.put(game, queue.size());
                int position = 0;
                for (QueueEntry entry : queue) {
                    places.put(entry.playerUuid(), new QueuePlace(game, ++position, queue.size()));
                }
            }
            view = new View(List.copyOf(arenas), Map.copyOf(sizes), Map.copyOf(places));
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not refresh the network snapshot", e);
        }
    }
}
