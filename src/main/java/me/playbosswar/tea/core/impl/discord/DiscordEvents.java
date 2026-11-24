package me.playbosswar.tea.core.impl.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.combat.MatchStats;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Each server posts only what happens on it, so a network never posts an event twice. */
public final class DiscordEvents implements Listener {

    private final TeaCoreAPI core;
    private final DiscordWebhooks hooks;
    private final Map<ArenaId, Instant> liveSince = new ConcurrentHashMap<>();

    public DiscordEvents(TeaCoreAPI core, DiscordWebhooks hooks) {
        this.core = core;
        this.hooks = hooks;
    }

    private String text(String key, Map<String, String> values) {
        MessageService ms = core.messageService();
        return ms.renderPlainText(ms.defaultLocale(), key, values);
    }

    private String gameName(GameId game) {
        String key = core.games().get(game).map(d -> d.displayNameKey()).orElse(null);
        return key == null ? game.value() : text(key, Map.of());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onState(ArenaStateChangeEvent event) {
        if (event.to() == ArenaState.LIVE) {
            liveSince.put(event.arena().id(), Instant.now());
        } else if (event.to() == ArenaState.RELEASED) {
            liveSince.remove(event.arena().id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        if (!hooks.enabled(DiscordWebhooks.Event.MATCH_RESULTS)) {
            return;
        }
        Arena arena = event.arena();
        String game = gameName(arena.gameId());
        String map = arena.mapId().flatMap(m -> core.maps().get(arena.gameId(), m))
                .map(MapDefinition::resolvedDisplayName).orElse(arena.mapId().orElse("-"));
        String winner = event.winningTeam()
                .flatMap(t -> core.teams().teamsFor(arena.id()).stream().filter(x -> x.id().equals(t)).findFirst())
                .map(t -> text(t.displayNameKey(), Map.of()))
                .orElseGet(() -> event.winner().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName)
                        .orElse(text("core.discord.match.draw", Map.of())));
        Instant start = liveSince.remove(arena.id());
        long seconds = start == null ? 0 : Duration.between(start, Instant.now()).getSeconds();

        record Line(String name, String team, MatchStats stats) { }
        List<Line> lines = new ArrayList<>();
        for (UUID uuid : arena.players()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            String team = core.teams().teamOf(arena.id(), uuid).map(Team::displayNameKey)
                    .map(k -> text(k, Map.of())).orElse("");
            lines.add(new Line(name == null ? uuid.toString().substring(0, 8) : name, team,
                    core.combat().matchStats(arena.id(), uuid)));
        }
        lines.sort(Comparator.comparingInt((Line l) -> l.stats().kills()).reversed());

        List<DiscordWebhooks.Field> fields = new ArrayList<>();
        fields.add(new DiscordWebhooks.Field(text("core.discord.match.winner", Map.of()), winner, true));
        fields.add(new DiscordWebhooks.Field(text("core.discord.match.duration", Map.of()),
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), true));
        if (!lines.isEmpty() && lines.get(0).stats().kills() > 0) {
            fields.add(new DiscordWebhooks.Field(text("core.discord.match.mvp", Map.of()),
                    lines.get(0).name() + " (" + lines.get(0).stats().kills() + ")", true));
        }
        StringBuilder board = new StringBuilder();
        for (Line l : lines.subList(0, Math.min(15, lines.size()))) {
            board.append(text("core.discord.match.player-line", Map.of(
                    "player", l.name(), "team", l.team(),
                    "kills", String.valueOf(l.stats().kills()),
                    "deaths", String.valueOf(l.stats().deaths()),
                    "assists", String.valueOf(l.stats().assists())))).append('\n');
        }
        if (board.length() > 0) {
            fields.add(new DiscordWebhooks.Field(text("core.discord.match.players", Map.of()), board.toString(), false));
        }
        hooks.post(DiscordWebhooks.Event.MATCH_RESULTS,
                text("core.discord.match.title", Map.of("game", game, "map", map)), null, fields);
    }

    /** e.g. actor "Notch", action "ended the match in fireball #63dfe98c". */
    public void staff(String actor, String action) {
        hooks.post(DiscordWebhooks.Event.STAFF_ACTIONS, text("core.discord.staff.title", Map.of()),
                text("core.discord.staff.line", Map.of("actor", actor, "action", action)), List.of());
    }

    public void achievement(String player, GameId game, String achievementName) {
        hooks.post(DiscordWebhooks.Event.ACHIEVEMENTS, text("core.discord.achievement.title", Map.of()),
                text("core.discord.achievement.line", Map.of("player", player, "game", gameName(game),
                        "name", achievementName)), List.of());
    }

    public void serverOnline(String serverId) {
        hooks.post(DiscordWebhooks.Event.SERVER_STATUS,
                text("core.discord.server.online", Map.of("server", serverId)), null, List.of());
    }

    public void serverOffline(String serverId) {
        hooks.post(DiscordWebhooks.Event.SERVER_STATUS,
                text("core.discord.server.offline", Map.of("server", serverId)), null, List.of());
    }
}
