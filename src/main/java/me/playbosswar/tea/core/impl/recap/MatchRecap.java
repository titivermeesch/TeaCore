package me.playbosswar.tea.core.impl.recap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.api.team.TeamId;
import me.playbosswar.tea.core.impl.rating.RatingService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * A personal summary for every player when a match ends: result, map and length,
 * their kills, deaths, assists, damage and place, the MVP, and their rating
 * change in ranked matches. Turned off with {@code features.match-recap}, also
 * per game.
 */
public final class MatchRecap implements Listener {

    private static final long DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final @Nullable RatingService ratings;
    private final Map<ArenaId, Long> liveSince = new ConcurrentHashMap<>();
    private final Map<ArenaId, Map<UUID, Integer>> ratingAtStart = new ConcurrentHashMap<>();

    public MatchRecap(Plugin plugin, TeaCoreAPI core, @Nullable RatingService ratings) {
        this.plugin = plugin;
        this.core = core;
        this.ratings = ratings;
    }

    @EventHandler
    public void onState(ArenaStateChangeEvent event) {
        Arena arena = event.arena();
        if (event.to() == ArenaState.LIVE) {
            liveSince.put(arena.id(), System.currentTimeMillis());
            if (ratings != null && ratings.ranked(arena.id()).orElse(false)) {
                Map<UUID, Integer> start = new HashMap<>();
                for (UUID uuid : arena.players()) {
                    start.put(uuid, ratings.get(uuid, arena.gameId()).rating());
                }
                ratingAtStart.put(arena.id(), start);
            }
        } else if (event.to() == ArenaState.RELEASED) {
            liveSince.remove(arena.id());
            ratingAtStart.remove(arena.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        Arena arena = event.arena();
        if (!core.features().enabled(arena.gameId(), "match-recap")) {
            return;
        }
        List<Mvp.Line> ranking = Mvp.ranking(core, arena, arena.players());
        UUID mvp = Mvp.of(ranking);
        TeamId winningTeam = event.winningTeam().orElse(null);
        UUID winner = event.winner().orElse(null);
        Long since = liveSince.remove(arena.id());
        long seconds = since == null ? 0 : (System.currentTimeMillis() - since) / 1000;
        String map = arena.mapId().map(id -> core.maps().get(arena.gameId(), id)
                .map(d -> d.displayName() == null ? id : d.displayName()).orElse(id)).orElse("-");
        Map<UUID, Integer> startRatings = ratingAtStart.remove(arena.id());
        String mvpName = mvp == null ? null : nameOf(mvp);
        Mvp.Line mvpLine = mvp == null ? null : ranking.get(0);

        Map<UUID, Map<String, String>> perPlayer = new LinkedHashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            Mvp.Line line = ranking.get(i);
            String team = core.teams().teamOf(arena.id(), line.uuid()).map(Team::id).map(TeamId::value).orElse(null);
            String result = winningTeam == null && winner == null ? "draw"
                    : winningTeam != null ? (winningTeam.value().equals(team) ? "win" : "loss")
                    : (line.uuid().equals(winner) ? "win" : "loss");
            Map<String, String> v = new HashMap<>();
            v.put("result", result);
            v.put("map", map);
            v.put("duration", String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
            v.put("kills", String.valueOf(line.stats().kills()));
            v.put("deaths", String.valueOf(line.stats().deaths()));
            v.put("assists", String.valueOf(line.stats().assists()));
            v.put("hearts", String.valueOf(Math.round(line.damage() / 2.0)));
            v.put("place", String.valueOf(i + 1));
            v.put("players", String.valueOf(ranking.size()));
            if (startRatings != null && startRatings.containsKey(line.uuid())) {
                v.put("rating-before", String.valueOf(startRatings.get(line.uuid())));
            }
            perPlayer.put(line.uuid(), v);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<UUID, Map<String, String>> e : perPlayer.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) {
                    continue;
                }
                Map<String, String> v = e.getValue();
                core.messageService().send(p, "core.recap.header", v);
                core.messageService().send(p, "core.recap." + v.get("result"), v);
                core.messageService().send(p, "core.recap.you", v);
                if (mvpName != null) {
                    core.messageService().send(p, Objects.equals(e.getKey(), mvpLine.uuid())
                            ? "core.recap.mvp-you" : "core.recap.mvp", Map.of("player", mvpName,
                            "kills", String.valueOf(mvpLine.stats().kills())));
                }
                if (v.containsKey("rating-before") && ratings != null) {
                    int before = Integer.parseInt(v.get("rating-before"));
                    int after = ratings.get(e.getKey(), arena.gameId()).rating();
                    int delta = after - before;
                    core.messageService().send(p, "core.recap.rating", Map.of("rating", String.valueOf(after),
                            "change", (delta >= 0 ? "+" : "") + delta));
                }
                core.messageService().send(p, "core.recap.footer", v);
            }
        }, DELAY_TICKS);
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }
}
