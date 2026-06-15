package me.playbosswar.tea.core.impl.scoreboard;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Owns per-player sidebar scoreboards for TeaCore. Features contribute content
 * by registering a {@link SidebarProvider} rather than each managing its own
 * Bukkit scoreboard and tick loop. Twice a second the manager asks every
 * provider whether it wants a given player's sidebar and renders the
 * highest-priority one that applies, clearing the board when none do.
 */
public final class ScoreboardManager implements Listener {

    private static final String OBJECTIVE = "tea_sidebar";
    private static final String TEAM_PREFIX = "tea_s";

    private final Plugin plugin;
    private final List<SidebarProvider> providers = new CopyOnWriteArrayList<>();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public ScoreboardManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registers a content provider. Providers are polled in registration order. */
    public void register(SidebarProvider provider) {
        providers.add(provider);
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new HashSet<>(active)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null)
                clear(p);
        }
        active.clear();
    }

    /**
     * Recomputes and applies the player's sidebar immediately, picking the
     * highest-priority provider that applies. Call after an event that should
     * update the board without waiting for the next tick (e.g. a toggle).
     */
    public void refresh(Player player) {
        SidebarProvider.Sidebar best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (SidebarProvider provider : providers) {
            Optional<SidebarProvider.Sidebar> candidate = provider.sidebarFor(player);
            if (candidate.isPresent() && provider.priority() > bestPriority) {
                bestPriority = provider.priority();
                best = candidate.get();
            }
        }
        if (best == null)
            clear(player);
        else
            apply(player, best.title(), best.lines());
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    private void apply(Player player, String title, List<String> lines) {
        Scoreboard board = player.getScoreboard();
        if (board == null || board.getObjective(OBJECTIVE) == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE, "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.setDisplayName(title);
        }

        // Sidebar entries sort by descending score, so the first line gets the
        // highest. Each line uses a unique invisible entry token and keeps its
        // visible text in a per-slot team prefix, so blank/duplicate lines don't
        // collide on the entry name.
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            String token = slotToken(i);
            Team team = board.getTeam(TEAM_PREFIX + i);
            if (team == null)
                team = board.registerNewTeam(TEAM_PREFIX + i);
            if (!team.hasEntry(token)) {
                for (String e : new HashSet<>(team.getEntries()))
                    team.removeEntry(e);
                team.addEntry(token);
            }
            setLine(team, lines.get(i));
            obj.getScore(token).setScore(n - i);
        }
        for (int i = n;; i++) {
            Team team = board.getTeam(TEAM_PREFIX + i);
            if (team == null)
                break;
            board.resetScores(slotToken(i));
            team.unregister();
        }
        active.add(player.getUniqueId());
    }

    private void clear(Player player) {
        active.remove(player.getUniqueId());
        Scoreboard board = player.getScoreboard();
        if (board != null && board.getObjective(OBJECTIVE) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** A unique, invisible per-slot entry name built from colour codes. */
    private static String slotToken(int index) {
        ChatColor[] palette = ChatColor.values();
        return palette[index % palette.length].toString() + ChatColor.RESET;
    }

    private static void setLine(Team team, String line) {
        if (line.length() <= 64) {
            team.setPrefix(line);
            team.setSuffix("");
            return;
        }
        // Split across prefix/suffix and carry the trailing colour over so the
        // second half keeps its formatting.
        String prefix = line.substring(0, 64);
        String suffix = line.substring(64);
        if (suffix.length() > 48)
            suffix = suffix.substring(0, 48);
        team.setPrefix(prefix);
        team.setSuffix(ChatColor.getLastColors(prefix) + suffix);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }
}
