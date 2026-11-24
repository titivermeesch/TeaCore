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
 * Publishes on every player count or state change as well as on the 2s tick,
 * so signs and matchmakers don't lag up to 2 seconds behind a join or leave.
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

    private volatile me.playbosswar.tea.core.impl.rating.RatingService ratings;

    public void setRatings(me.playbosswar.tea.core.impl.rating.RatingService ratings) {
        this.ratings = ratings;
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
                now,
                arena.phaseSecondsRemaining(),
                roster(arena),
                sidebar(arena),
                ratings == null ? null : ratings.ranked(arena.id()).orElse(null),
                ratings == null ? 0 : ratings.average(arena),
                ratings == null ? 0 : ratings.range(arena));
        backend.publishArena(snapshot);
    }

    private java.util.List<ArenaSnapshot.RosterEntry> roster(Arena arena) {
        java.util.List<ArenaSnapshot.RosterEntry> out = new java.util.ArrayList<>();
        me.playbosswar.tea.core.api.team.TeamManager teams = null;
        try {
            teams = me.playbosswar.tea.core.api.TeaCoreAPI.get().teams();
        } catch (IllegalStateException ignored) {
            // API not ready
        }
        me.playbosswar.tea.core.api.combat.CombatTracker combat = null;
        try {
            combat = me.playbosswar.tea.core.api.TeaCoreAPI.get().combat();
        } catch (IllegalStateException ignored) {
            // API not ready
        }
        for (java.util.UUID uuid : arena.players()) {
            String team = teams == null ? null
                    : teams.teamOf(arena.id(), uuid).map(t -> t.id().value()).orElse(null);
            me.playbosswar.tea.core.api.combat.MatchStats stats = combat == null
                    ? me.playbosswar.tea.core.api.combat.MatchStats.ZERO
                    : combat.matchStats(arena.id(), uuid);
            out.add(new ArenaSnapshot.RosterEntry(uuid, nameOf(uuid), team, false,
                    stats.kills(), stats.deaths(), stats.assists()));
        }
        for (java.util.UUID uuid : arena.spectators()) {
            out.add(new ArenaSnapshot.RosterEntry(uuid, nameOf(uuid), null, true));
        }
        return out;
    }

    /** The game sidebar as the first online participant sees it. */
    private ArenaSnapshot.SidebarView sidebar(Arena arena) {
        if (!(plugin instanceof me.playbosswar.tea.core.impl.TeaCorePlugin core)
                || !(core.sidebars() instanceof me.playbosswar.tea.core.impl.sidebar.SidebarServiceImpl sidebars)) {
            return null;
        }
        for (java.util.UUID uuid : arena.players()) {
            org.bukkit.entity.Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null) {
                continue;
            }
            try {
                return sidebars.gameSidebar(viewer)
                        .map(sb -> new ArenaSnapshot.SidebarView(viewer.getName(), sb.title(), sb.lines()))
                        .orElse(null);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static String nameOf(java.util.UUID uuid) {
        org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
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
        // Purge on release so signs don't show a stale arena with non-zero player counts.
        if (event.to() == ArenaState.RELEASED) {
            backend.removeArena(event.arena().id());
            return;
        }
        publish(event.arena(), Instant.now());
    }
}
