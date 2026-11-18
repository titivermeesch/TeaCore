package me.playbosswar.tea.core.impl.sidebar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.combat.MatchStats;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The built-in values every sidebar line and {@code %tea_...%} placeholder can
 * use for a player in an arena, computed in one place so both always agree.
 * Also remembers each arena's last winner for the {@code ended} phase.
 */
public final class ArenaValues implements Listener {

    private final TeaCoreAPI core;
    private final java.util.function.Supplier<String> defaultLocale;
    private final Map<ArenaId, String> winners = new ConcurrentHashMap<>();

    /**
     * @param defaultLocale the server's default locale (i18n.default-locale), used
     *                      for values shared by every viewer such as the winner.
     */
    public ArenaValues(TeaCoreAPI core, java.util.function.Supplier<String> defaultLocale) {
        this.core = core;
        this.defaultLocale = defaultLocale;
    }

    /** Values for {@code viewer} in {@code arena}; keys are tag names without brackets. */
    public Map<String, String> forViewer(Player viewer, Arena arena) {
        Map<String, String> v = new HashMap<>();
        String locale = core.messageService().resolveLocale(viewer);
        v.put("player", viewer.getName());
        v.put("game", arena.gameId().value());
        v.put("game-name", gameName(locale, arena));
        v.put("arena", arena.id().value().toString().substring(0, 8));
        v.put("map-id", arena.mapId().orElse("-"));
        v.put("map", mapName(arena));
        v.put("phase", arena.state().name().toLowerCase(Locale.ROOT));
        v.put("players", String.valueOf(arena.players().size()));
        v.put("spectators", String.valueOf(arena.spectators().size()));
        v.put("min-players", String.valueOf(arena.minPlayers()));
        v.put("min", String.valueOf(arena.minPlayers()));
        v.put("max-players", String.valueOf(arena.maxPlayers()));
        v.put("waiting-for", String.valueOf(Math.max(0, arena.minPlayers() - arena.players().size())));
        int seconds = Math.max(0, arena.phaseSecondsRemaining());
        v.put("seconds", String.valueOf(seconds));
        v.put("time", formatTime(seconds));
        Optional<Team> team = core.teams().teamOf(arena.id(), viewer.getUniqueId());
        v.put("team", team.map(t -> teamName(locale, t)).orElse("-"));
        v.put("team-id", team.map(t -> t.id().value()).orElse("-"));
        Kit kit = core.kits().selectedKit(viewer, arena.gameId()).orElse(null);
        v.put("kit", kit == null ? "-" : core.messageService().renderPlainText(locale, kit.displayNameKey(), Map.of()));
        MatchStats stats = core.combat().matchStats(arena.id(), viewer.getUniqueId());
        v.put("kills", String.valueOf(stats.kills()));
        v.put("deaths", String.valueOf(stats.deaths()));
        v.put("assists", String.valueOf(stats.assists()));
        v.put("winner", winners.getOrDefault(arena.id(), "-"));
        return v;
    }

    /** Built-in columns of a {@code @players:} row. */
    public Map<String, String> playerRow(Player viewer, Arena arena, Player player) {
        String locale = core.messageService().resolveLocale(viewer);
        MatchStats stats = core.combat().matchStats(arena.id(), player.getUniqueId());
        Optional<Team> team = core.teams().teamOf(arena.id(), player.getUniqueId());
        Map<String, String> row = new HashMap<>();
        row.put("player-name", player.getName());
        row.put("player-you", player.equals(viewer) ? "true" : "false");
        row.put("player-kills", String.valueOf(stats.kills()));
        row.put("player-deaths", String.valueOf(stats.deaths()));
        row.put("player-assists", String.valueOf(stats.assists()));
        row.put("player-team", team.map(t -> teamName(locale, t)).orElse("-"));
        row.put("player-health", String.valueOf((int) Math.ceil(player.getHealth())));
        return row;
    }

    /** Built-in columns of a {@code @teams:} row; score defaults to the members' match kills. */
    public Map<String, String> teamRow(Player viewer, Arena arena, Team team) {
        String locale = core.messageService().resolveLocale(viewer);
        int kills = 0;
        int online = 0;
        for (UUID member : team.members()) {
            kills += core.combat().matchStats(arena.id(), member).kills();
            if (arena.players().contains(member)) {
                online++;
            }
        }
        Map<String, String> row = new HashMap<>();
        row.put("team-name", teamName(locale, team));
        row.put("team-id", team.id().value());
        row.put("team-size", String.valueOf(online));
        row.put("team-score", String.valueOf(kills));
        return row;
    }

    public String mapName(Arena arena) {
        return arena.mapId()
                .flatMap(id -> core.maps().get(arena.gameId(), id))
                .map(m -> m.resolvedDisplayName())
                .orElseGet(() -> arena.mapId().orElse("-"));
    }

    public String gameName(String locale, Arena arena) {
        return gameName(locale, arena.gameId());
    }

    /** A game's plain display name, honouring {@code game-display}. */
    public String gameName(String locale, me.playbosswar.tea.core.api.game.GameId game) {
        return display == null ? game.value() : display.plainName(locale, game);
    }

    private me.playbosswar.tea.core.impl.menu.GameDisplay display;

    /** Lets {@code <game-name>} honour {@code game-display} overrides. */
    public void setGameDisplay(me.playbosswar.tea.core.impl.menu.GameDisplay display) {
        this.display = display;
    }

    public String teamName(String locale, Team team) {
        return core.messageService().renderLegacy(locale, team.displayNameKey(), Map.of());
    }

    public Optional<String> winner(ArenaId arena) {
        return Optional.ofNullable(winners.get(arena));
    }

    public static String formatTime(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        Arena arena = event.arena();
        String name = event.winningTeam()
                .flatMap(teamId -> core.teams().teamsFor(arena.id()).stream()
                        .filter(t -> t.id().equals(teamId)).findFirst())
                .map(t -> teamName(defaultLocale.get(), t))
                .orElseGet(() -> event.winner().map(Bukkit::getOfflinePlayer)
                        .map(OfflinePlayer::getName).orElse("-"));
        winners.put(arena.id(), name);
    }

    @EventHandler
    public void onState(ArenaStateChangeEvent event) {
        if (event.to() == ArenaState.STARTING || event.to() == ArenaState.RELEASED) {
            winners.remove(event.arena().id());
        }
    }
}
