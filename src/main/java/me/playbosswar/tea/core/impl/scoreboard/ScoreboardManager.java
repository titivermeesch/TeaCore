package me.playbosswar.tea.core.impl.scoreboard;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import me.playbosswar.tea.core.api.sidebar.SidebarProvider;
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

public final class ScoreboardManager implements Listener {

    private static final String OBJECTIVE = "tea_sidebar";
    private static final String TEAM_PREFIX = "tea_s";

    private final Plugin plugin;
    private final List<SidebarProvider> providers = new CopyOnWriteArrayList<>();
    /** Highest priority first; the sort is stable so ties keep registration order. */
    private volatile List<SidebarProvider> byPriority = List.of();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public ScoreboardManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(SidebarProvider provider) {
        providers.add(provider);
        List<SidebarProvider> sorted = new java.util.ArrayList<>(providers);
        sorted.sort(java.util.Comparator.comparingInt(SidebarProvider::priority).reversed());
        byPriority = List.copyOf(sorted);
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 1L);
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

    /** Applies immediately instead of waiting for the next tick. */
    public void refresh(Player player) {
        SidebarProvider.Sidebar best = null;
        for (SidebarProvider provider : byPriority) {
            Optional<SidebarProvider.Sidebar> candidate = provider.sidebarFor(player);
            if (candidate.isPresent()) {
                best = candidate.get();
                break;
            }
        }
        if (best == null)
            clear(player);
        else
            apply(player, best.title(), best.lines());
    }

    private static final int REFRESH_TICKS = 10;
    private long tickCount;

    /** Refreshes a tenth of the players each tick to spread the work. */
    private void tick() {
        int slot = (int) (tickCount++ % REFRESH_TICKS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (Math.floorMod(p.getUniqueId().hashCode(), REFRESH_TICKS) == slot) {
                refresh(p);
            }
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
            obj = scoreboards().newObjective(board, OBJECTIVE, fitTitle(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.setDisplayName(fitTitle(title));
        }

        // Entries sort by descending score. Each line uses a unique invisible entry
        // and keeps its text in a per-slot team prefix, so blank or duplicate lines
        // don't collide on the entry name.
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
        syncTeamColors(player, board);
        active.add(player.getUniqueId());
    }

    private static final String ARENA_TEAM_PREFIX = "tea_at_";

    private void syncTeamColors(Player viewer, Scoreboard board) {
        java.util.Map<String, java.util.Set<String>> wanted = new java.util.HashMap<>();
        java.util.Map<String, ChatColor> colors = new java.util.HashMap<>();
        try {
            me.playbosswar.tea.core.api.TeaCoreAPI core = me.playbosswar.tea.core.api.TeaCoreAPI.get();
            core.arenas().arenaOf(viewer.getUniqueId()).ifPresent(arena -> {
                if (core.teams().isSingleTeam(arena.id())) {
                    return;
                }
                for (me.playbosswar.tea.core.api.team.Team team : core.teams().teamsFor(arena.id())) {
                    String name = ARENA_TEAM_PREFIX + team.id().value();
                    if (name.length() > 16) name = name.substring(0, 16);
                    java.util.Set<String> members = new java.util.HashSet<>();
                    for (UUID uuid : team.members()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) members.add(p.getName());
                    }
                    wanted.put(name, members);
                    colors.put(name, colorOf(team.colorStyleSlot()));
                }
            });
        } catch (IllegalStateException notReady) {
            return;
        }
        for (Team team : new HashSet<>(board.getTeams())) {
            if (team.getName().startsWith(ARENA_TEAM_PREFIX) && !wanted.containsKey(team.getName())) {
                team.unregister();
            }
        }
        for (var entry : wanted.entrySet()) {
            Team team = board.getTeam(entry.getKey());
            if (team == null) {
                team = board.registerNewTeam(entry.getKey());
            }
            ChatColor color = colors.get(entry.getKey());
            scoreboards().setTeamColor(team, color);
            for (String e : new HashSet<>(team.getEntries())) {
                if (!entry.getValue().contains(e)) team.removeEntry(e);
            }
            for (String name : entry.getValue()) {
                if (!team.hasEntry(name)) team.addEntry(name);
            }
        }
    }

    private static ChatColor colorOf(String slot) {
        if (slot == null) return ChatColor.WHITE;
        // Theme slots look like "team.red"; the colour is the last part.
        slot = slot.substring(slot.lastIndexOf('.') + 1);
        return switch (slot.toLowerCase(java.util.Locale.ROOT)) {
            case "red" -> ChatColor.RED;
            case "blue" -> ChatColor.BLUE;
            case "green" -> ChatColor.DARK_GREEN;
            case "lime" -> ChatColor.GREEN;
            case "yellow" -> ChatColor.YELLOW;
            case "purple" -> ChatColor.DARK_PURPLE;
            case "pink" -> ChatColor.LIGHT_PURPLE;
            case "orange", "gold" -> ChatColor.GOLD;
            case "aqua", "cyan" -> ChatColor.AQUA;
            case "gray", "grey" -> ChatColor.GRAY;
            default -> {
                try {
                    yield ChatColor.valueOf(slot.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    yield ChatColor.WHITE;
                }
            }
        };
    }

    private void clear(Player player) {
        active.remove(player.getUniqueId());
        Scoreboard board = player.getScoreboard();
        if (board != null && board.getObjective(OBJECTIVE) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private static String slotToken(int index) {
        ChatColor[] palette = ChatColor.values();
        return palette[index % palette.length].toString() + ChatColor.RESET;
    }

    private static me.playbosswar.tea.core.api.platform.Scoreboards scoreboards() {
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().scoreboards();
    }

    private static String fitTitle(String title) {
        int max = scoreboards().maxTitleLength();
        return title.length() <= max ? title : title.substring(0, max);
    }

    private static void setLine(Team team, String line) {
        int max = scoreboards().maxAffixLength();
        if (line.length() <= max) {
            team.setPrefix(line);
            team.setSuffix("");
            return;
        }
        // Split across prefix/suffix and carry the trailing colour over so the
        // second half keeps its formatting. Don't split a colour code.
        int cut = line.charAt(max - 1) == ChatColor.COLOR_CHAR ? max - 1 : max;
        String prefix = line.substring(0, cut);
        String suffix = ChatColor.getLastColors(prefix) + line.substring(cut);
        if (suffix.length() > max)
            suffix = suffix.substring(0, max);
        team.setPrefix(prefix);
        team.setSuffix(suffix);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }
}
