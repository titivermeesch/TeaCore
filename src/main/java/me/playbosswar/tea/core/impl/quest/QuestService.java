package me.playbosswar.tea.core.impl.quest;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.combat.ArenaKillEvent;
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Quest picks derive from the player's uuid and the period, so every server agrees without storing assignments. */
public final class QuestService implements Listener {

    public enum Goal { WINS, GAMES, KILLS, ASSISTS }

    public record Quest(String id, boolean weekly, String name, Goal goal, int amount, String game,
                        double coins, List<String> commands) { }

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final DataSource dataSource;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TeaCore-quests");
        t.setDaemon(true);
        return t;
    });
    private volatile Map<String, Quest> daily = Map.of();
    private volatile Map<String, Quest> weekly = Map.of();
    private volatile int dailyCount = 3;
    private volatile int weeklyCount = 2;
    private volatile int resetHour;
    /** Key: "period|quest". A missing player hasn't loaded yet. */
    private final Map<UUID, Map<String, Integer>> progress = new ConcurrentHashMap<>();

    public QuestService(Plugin plugin, TeaCoreAPI core, DataSource dataSource, Logger logger) {
        this.plugin = plugin;
        this.core = core;
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public boolean enabled() {
        return core.features().enabled("quests");
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        dailyCount = Math.max(0, yaml.getInt("settings.daily-count", 3));
        weeklyCount = Math.max(0, yaml.getInt("settings.weekly-count", 2));
        resetHour = Math.max(0, Math.min(23, yaml.getInt("settings.reset-hour", 0)));
        daily = parse(yaml.getConfigurationSection("daily"), false);
        weekly = parse(yaml.getConfigurationSection("weekly"), true);
    }

    private Map<String, Quest> parse(ConfigurationSection section, boolean isWeekly) {
        Map<String, Quest> out = new LinkedHashMap<>();
        if (section == null) {
            return out;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Goal goal;
            try {
                goal = Goal.valueOf(s.getString("goal", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("Quest " + id + ": unknown goal '" + s.getString("goal") + "', skipped");
                continue;
            }
            out.put(id, new Quest(id, isWeekly, s.getString("name", id), goal, Math.max(1, s.getInt("amount", 1)),
                    s.getString("game", "any").toLowerCase(Locale.ROOT), s.getDouble("reward.coins", 0),
                    List.copyOf(s.getStringList("reward.commands"))));
        }
        return out;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault()).minusHours(resetHour);
    }

    public String dayKey() {
        return "d" + now().toLocalDate();
    }

    public String weekKey() {
        LocalDate d = now().toLocalDate();
        return "w" + d.get(IsoFields.WEEK_BASED_YEAR) + "-" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public Duration resetsIn(boolean isWeekly) {
        LocalDateTime n = now();
        LocalDateTime next = isWeekly
                ? n.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
                : n.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(n, next);
    }

    public List<Quest> questsOf(UUID player, boolean isWeekly) {
        Map<String, Quest> pool = isWeekly ? weekly : daily;
        int count = Math.min(isWeekly ? weeklyCount : dailyCount, pool.size());
        String period = isWeekly ? weekKey() : dayKey();
        List<Quest> sorted = new ArrayList<>(pool.values());
        // Order by a hash of player + period + quest: a stable shuffle per player and period.
        sorted.sort(Comparator.comparingLong(q -> mix((player + "|" + period + "|" + q.id()).hashCode())));
        return sorted.subList(0, count);
    }

    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        return x;
    }

    private static String key(String period, String questId) {
        return period + "|" + questId;
    }

    public int progressOf(UUID player, Quest q) {
        Map<String, Integer> p = progress.get(player);
        return p == null ? 0 : p.getOrDefault(key(q.weekly() ? weekKey() : dayKey(), q.id()), 0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        progress.remove(event.getPlayer().getUniqueId());
    }

    public void load(UUID uuid) {
        String day = dayKey();
        String week = weekKey();
        io.execute(() -> {
            Map<String, Integer> p = new ConcurrentHashMap<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT period_key, quest_id, progress FROM "
                         + "tea_quest_progress WHERE player_uuid = ? AND period_key IN (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, day);
                ps.setString(3, week);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        p.put(key(rs.getString(1), rs.getString(2)), rs.getInt(3));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Could not load quests of " + uuid, e);
            }
            progress.put(uuid, p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(ArenaKillEvent event) {
        GameId game = event.arena().gameId();
        if (event.credit().killer() != null) {
            add(event.credit().killer(), game, Goal.KILLS, 1);
        }
        for (UUID assist : event.credit().assists()) {
            add(assist, game, Goal.ASSISTS, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        Arena arena = event.arena();
        for (UUID uuid : arena.players()) {
            add(uuid, arena.gameId(), Goal.GAMES, 1);
            boolean won = event.winningTeam()
                    .map(t -> core.teams().teamOf(arena.id(), uuid).map(team -> team.id().equals(t)).orElse(false))
                    .orElseGet(() -> event.winner().map(uuid::equals).orElse(false));
            if (won) {
                add(uuid, arena.gameId(), Goal.WINS, 1);
            }
        }
    }

    private void add(UUID uuid, GameId game, Goal goal, int amount) {
        if (!enabled()) {
            return;
        }
        Map<String, Integer> p = progress.get(uuid);
        if (p == null) {
            return; // not loaded (or not on this server)
        }
        for (boolean isWeekly : new boolean[] {false, true}) {
            String period = isWeekly ? weekKey() : dayKey();
            for (Quest q : questsOf(uuid, isWeekly)) {
                if (q.goal() != goal || !(q.game().equals("any") || q.game().equals(game.value()))) {
                    continue;
                }
                String k = key(period, q.id());
                int before = p.getOrDefault(k, 0);
                if (before >= q.amount()) {
                    continue;
                }
                int after = Math.min(q.amount(), before + amount);
                p.put(k, after);
                save(uuid, period, q.id(), after, after >= q.amount());
                if (after >= q.amount()) {
                    complete(uuid, q);
                }
            }
        }
    }

    private void complete(UUID uuid, Quest q) {
        Player player = Bukkit.getPlayer(uuid);
        String reward = rewardText(q);
        if (q.coins() > 0) {
            EconomyService economy = core.economy();
            if (economy != null && economy.isAvailable()) {
                economy.deposit(uuid, q.coins());
            }
        }
        String name = player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
        for (String command : q.commands()) {
            String cmd = command.replace("{player}", name == null ? uuid.toString() : name);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
        if (player != null) {
            core.messageService().send(player, "core.quests.completed", Map.of("quest", q.name(), "reward", reward));
        }
    }

    public String rewardText(Quest q) {
        EconomyService economy = core.economy();
        if (q.coins() > 0) {
            return economy != null && economy.isAvailable() ? economy.format(q.coins())
                    : String.valueOf((long) q.coins());
        }
        return q.commands().isEmpty() ? "-" : "a prize";
    }

    private void save(UUID uuid, String period, String questId, int value, boolean done) {
        io.execute(() -> {
            try (Connection c = dataSource.getConnection()) {
                int updated;
                try (PreparedStatement up = c.prepareStatement("UPDATE tea_quest_progress SET progress = ?, "
                        + "completed = ?, updated_at = ? WHERE player_uuid = ? AND period_key = ? AND quest_id = ?")) {
                    up.setInt(1, value);
                    up.setInt(2, done ? 1 : 0);
                    up.setLong(3, System.currentTimeMillis());
                    up.setString(4, uuid.toString());
                    up.setString(5, period);
                    up.setString(6, questId);
                    updated = up.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO tea_quest_progress (player_uuid, "
                            + "period_key, quest_id, progress, completed, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                        ins.setString(1, uuid.toString());
                        ins.setString(2, period);
                        ins.setString(3, questId);
                        ins.setInt(4, value);
                        ins.setInt(5, done ? 1 : 0);
                        ins.setLong(6, System.currentTimeMillis());
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Could not save quest progress of " + uuid, e);
            }
        });
    }

    public void cleanUp() {
        io.execute(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM tea_quest_progress WHERE updated_at < ?")) {
                ps.setLong(1, System.currentTimeMillis() - Duration.ofDays(14).toMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.FINE, "Quest clean-up failed", e);
            }
        });
    }

    public void close() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void register(MenuService menus) {
        menus.registerSource("quests-daily", (viewer, args) -> entries(viewer, false));
        menus.registerSource("quests-weekly", (viewer, args) -> entries(viewer, true));
    }

    private List<MenuEntry> entries(Player viewer, boolean isWeekly) {
        List<MenuEntry> out = new ArrayList<>();
        Duration left = resetsIn(isWeekly);
        String resets = left.toDays() > 0 ? left.toDays() + "d " + left.toHoursPart() + "h"
                : left.toHours() + "h " + left.toMinutesPart() + "m";
        for (Quest q : questsOf(viewer.getUniqueId(), isWeekly)) {
            int done = progressOf(viewer.getUniqueId(), q);
            Map<String, String> v = new HashMap<>();
            v.put("quest_id", q.id());
            v.put("name", q.name());
            v.put("period", isWeekly ? "weekly" : "daily");
            v.put("progress", String.valueOf(done));
            v.put("amount", String.valueOf(q.amount()));
            v.put("bar", bar(done, q.amount()));
            v.put("done", String.valueOf(done >= q.amount()));
            v.put("reward", rewardText(q));
            v.put("resets_in", resets);
            v.put("icon", done >= q.amount() ? "LIME_DYE" : switch (q.goal()) {
                case WINS -> "GOLD_INGOT";
                case GAMES -> "CLOCK";
                case KILLS -> "IRON_SWORD";
                case ASSISTS -> "SHIELD";
            });
            out.add(MenuEntry.of(v));
        }
        return out;
    }

    private static String bar(int done, int amount) {
        int filled = (int) Math.round(10.0 * Math.min(done, amount) / amount);
        // Section codes: menu values are inserted as text, not parsed as MiniMessage.
        return "\u00a7a" + "|".repeat(filled) + "\u00a78" + "|".repeat(10 - filled);
    }
}
