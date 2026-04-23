package me.playbosswar.tea.core.impl.coordination;

import java.time.Instant;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Publishes arena snapshots to the coordination backend on a 2s tick AND
 * immediately
 * when player counts or state change, so connection signs / matchmakers don't
 * lag
 * behind reality by up to 2 seconds after a join or leave.
 */
public final class ArenaHeartbeater implements Listener {

    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final CoordinationBackend backend;
    private BukkitTask task;

    public ArenaHeartbeater(Plugin plugin, ArenaManager arenaManager, CoordinationBackend backend) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.backend = backend;
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Arena arena : arenaManager.all()) {
            backend.removeArena(arena.id());
        }
    }

    private void tick() {
        Instant now = Instant.now();
        for (Arena arena : arenaManager.all()) {
            publish(arena, now);
        }
    }

    private void publish(Arena arena, Instant now) {
        ArenaSnapshot snapshot = new ArenaSnapshot(
                backend.serverId(),
                arena.id(),
                arena.gameId(),
                arena.mapId().orElse(""),
                arena.state(),
                arena.players().size(),
                arena.minPlayers(),
                arena.maxPlayers(),
                now);
        backend.publishArena(snapshot);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ArenaPlayerJoinEvent event) {
        publish(event.arena(), Instant.now());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        publish(event.arena(), Instant.now());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onState(ArenaStateChangeEvent event) {
        // On release, purge the snapshot outright so signs don't show a stale ghost
        // arena with non-zero player counts.
        if (event.to() == ArenaState.RELEASED) {
            backend.removeArena(event.arena().id());
            return;
        }
        publish(event.arena(), Instant.now());
    }
}
