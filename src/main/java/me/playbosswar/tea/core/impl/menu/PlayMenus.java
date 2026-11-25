package me.playbosswar.tea.core.impl.menu;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.achievement.AchievementDefinition;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.history.MatchHistoryService;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class PlayMenus {

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final MatchHistoryService history;
    private final GameDisplay display;

    public PlayMenus(Plugin plugin, TeaCoreAPI core, MatchHistoryService history, GameDisplay display) {
        this.plugin = plugin;
        this.core = core;
        this.history = history;
        this.display = display;
    }

    public void register(MenuService menus) {
        menus.registerSource("games", (viewer, args) -> games(viewer));
        menus.registerSource("maps", this::maps);
        menus.registerSource("arenas", this::arenas);
        menus.registerSource("achievements", this::achievements);
        menus.registerSource("match-history", this::history);
        menus.registerAction("play", (click, arg) -> {
            click.player().closeInventory();
            click.player().performCommand("play " + arg.trim());
        });
        menus.registerAction("join-arena", (click, arg) -> {
            click.player().closeInventory();
            enter(click.player(), arg.trim(), false);
        });
        menus.registerAction("spectate", (click, arg) -> {
            click.player().closeInventory();
            enter(click.player(), arg.trim(), true);
        });
    }

    /**
     * An arena on another server can't be entered directly, so the player queues for its game and map.
     */
    private void enter(Player player, String arenaId, boolean spectate) {
        // Full ids or the short 8-character form.
        ArenaSnapshot match = core.coordination().allArenas().stream()
                .filter(a -> a.arenaId().value().toString().startsWith(arenaId.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        if (match == null) {
            return;
        }
        me.playbosswar.tea.core.api.arena.ArenaId key = match.arenaId();
        if (core.arenas().get(key).isPresent()) {
            core.arenas().addPlayer(key, player, spectate);
            return;
        }
        player.performCommand("play " + match.gameId().value() + (match.mapId() == null ? "" : " " + match.mapId()));
    }

    /** Local games plus games other servers host (multi-server lobbies). */
    private Set<GameId> allGames() {
        Set<GameId> ids = new LinkedHashSet<>();
        core.games().all().forEach(def -> ids.add(def.id()));
        try {
            ids.addAll(core.coordination().allHostedGames());
        } catch (RuntimeException ignored) {
            // standalone backends may not track hosted games
        }
        return ids;
    }

    private List<MenuEntry> games(Player viewer) {
        List<ArenaSnapshot> arenas = List.copyOf(core.coordination().allArenas());
        List<MenuEntry> out = new ArrayList<>();
        for (GameId game : allGames()) {
            int players = 0;
            int total = 0;
            int open = 0;
            for (ArenaSnapshot a : arenas) {
                if (a.gameId().equals(game)) {
                    total++;
                    players += a.players();
                    if (a.state() == ArenaState.WAITING || a.state() == ArenaState.COUNTDOWN) {
                        open++;
                    }
                }
            }
            Map<String, String> v = new HashMap<>();
            v.put("game_id", game.value());
            v.put("game_name", gameName(viewer, game));
            v.put("players", String.valueOf(players));
            v.put("arenas", String.valueOf(total));
            v.put("open_arenas", String.valueOf(open));
            v.put("queue", String.valueOf(core.coordination().queueFor(game).size()));
            v.put("maps", String.valueOf(core.maps().forGame(game).size()));
            out.add(new MenuEntry(v, new ItemStack(gameIcon(game))));
        }
        return out;
    }

    public String gameName(Player viewer, GameId game) {
        return display.name(core.messageService().resolveLocale(viewer), game);
    }

    private Material gameIcon(GameId game) {
        return display.icon(game);
    }

    private List<MenuEntry> maps(Player viewer, Map<String, String> args) {
        String gameArg = args.get("game");
        if (gameArg == null) {
            return List.of();
        }
        GameId game = GameId.of(gameArg);
        List<ArenaSnapshot> arenas = core.coordination().allArenas().stream()
                .filter(a -> a.gameId().equals(game)).toList();
        List<MenuEntry> out = new ArrayList<>();
        for (MapDefinition map : core.maps().forGame(game)) {
            int players = arenas.stream().filter(a -> map.mapId().equals(a.mapId())).mapToInt(ArenaSnapshot::players).sum();
            Map<String, String> v = new HashMap<>();
            v.put("game", game.value());
            v.put("game_name", gameName(viewer, game));
            v.put("map_id", map.mapId());
            v.put("map_name", map.resolvedDisplayName());
            v.put("min", String.valueOf(map.minPlayers()));
            v.put("max", String.valueOf(map.maxPlayers()));
            v.put("players", String.valueOf(players));
            out.add(new MenuEntry(v, me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr("FILLED_MAP", "PAPER", 1)));
        }
        return out;
    }

    private List<MenuEntry> arenas(Player viewer, Map<String, String> args) {
        String gameArg = args.get("game");
        String mapArg = args.get("map");
        List<MenuEntry> out = new ArrayList<>();
        for (ArenaSnapshot a : core.coordination().allArenas()) {
            if ((gameArg != null && !a.gameId().value().equals(gameArg))
                    || (mapArg != null && !mapArg.isEmpty() && !mapArg.equals(a.mapId()))) {
                continue;
            }
            Map<String, String> v = new HashMap<>();
            v.put("arena_id", a.arenaId().value().toString().substring(0, 8));
            v.put("arena_uuid", a.arenaId().value().toString());
            v.put("game", a.gameId().value());
            v.put("game_name", gameName(viewer, a.gameId()));
            v.put("map_id", a.mapId() == null ? "-" : a.mapId());
            v.put("map_name", mapName(a));
            v.put("state", a.state().name().toLowerCase(Locale.ROOT));
            v.put("players", String.valueOf(a.players()));
            v.put("max", String.valueOf(a.maxPlayers()));
            v.put("server", a.serverId());
            v.put("joinable", String.valueOf((a.state() == ArenaState.WAITING || a.state() == ArenaState.COUNTDOWN)
                    && a.players() < a.maxPlayers()));
            out.add(new MenuEntry(v, stateIcon(a.state())));
        }
        return out;
    }

    private String mapName(ArenaSnapshot a) {
        if (a.mapId() == null) {
            return "-";
        }
        return core.maps().get(a.gameId(), a.mapId()).map(MapDefinition::resolvedDisplayName).orElse(a.mapId());
    }

    private static ItemStack stateIcon(ArenaState state) {
        String wool = switch (state) {
            case WAITING -> "LIME_WOOL";
            case COUNTDOWN, STARTING -> "YELLOW_WOOL";
            case LIVE -> "RED_WOOL";
            default -> "GRAY_WOOL";
        };
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr(wool, "WHITE_WOOL", 1);
    }

    private List<MenuEntry> achievements(Player viewer, Map<String, String> args) {
        MessageService ms = core.messageService();
        String locale = ms.resolveLocale(viewer);
        UUID target = args.containsKey("target_uuid") ? UUID.fromString(args.get("target_uuid")) : viewer.getUniqueId();
        List<MenuEntry> out = new ArrayList<>();
        for (GameDefinition def : core.games().all()) {
            if (args.containsKey("game") && !def.id().value().equals(args.get("game"))) {
                continue;
            }
            for (AchievementDefinition a : core.achievements().forGame(def.id())) {
                boolean unlocked = unlockedCached(target, def.id(), a.id());
                Map<String, String> v = new HashMap<>();
                v.put("game", def.id().value());
                v.put("game_name", gameName(viewer, def.id()));
                v.put("achievement_id", a.id());
                v.put("name", ms.renderLegacyNoPrefix(locale, a.nameKey(), Map.of()));
                v.put("desc", ms.renderLegacyNoPrefix(locale, a.descKey(), Map.of()));
                v.put("unlocked", String.valueOf(unlocked));
                v.put("status", ms.renderLegacyNoPrefix(locale,
                        unlocked ? "core.achievements.unlocked" : "core.achievements.locked", Map.of()));
                out.add(new MenuEntry(v, unlocked ? new ItemStack(a.icon()) : me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr("GRAY_DYE", "PAPER", 1)));
            }
        }
        // Games this server doesn't run: the definitions their servers stored.
        if (core.achievements() instanceof me.playbosswar.tea.core.impl.achievement.AchievementServiceImpl impl) {
            for (var a : impl.storedCached()) {
                if (core.games().get(a.gameId()).isPresent()
                        || (args.containsKey("game") && !a.gameId().value().equals(args.get("game")))) {
                    continue;
                }
                boolean unlocked = unlockedCached(target, a.gameId(), a.id());
                Map<String, String> v = new HashMap<>();
                v.put("game", a.gameId().value());
                v.put("game_name", gameName(viewer, a.gameId()));
                v.put("achievement_id", a.id());
                v.put("name", a.name());
                v.put("desc", a.description());
                v.put("unlocked", String.valueOf(unlocked));
                v.put("status", ms.renderLegacyNoPrefix(locale,
                        unlocked ? "core.achievements.unlocked" : "core.achievements.locked", Map.of()));
                out.add(new MenuEntry(v, unlocked ? new ItemStack(a.icon()) : me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr("GRAY_DYE", "PAPER", 1)));
            }
        }
        return out;
    }

    private boolean unlockedCached(UUID target, GameId game, String id) {
        if (core.achievements() instanceof me.playbosswar.tea.core.impl.achievement.AchievementServiceImpl impl) {
            return impl.isUnlockedCached(target, game, id);
        }
        return core.achievements().isUnlocked(target, game, id);
    }

    private List<MenuEntry> history(Player viewer, Map<String, String> args) {
        MessageService ms = core.messageService();
        String locale = ms.resolveLocale(viewer);
        UUID target = args.containsKey("target_uuid") ? UUID.fromString(args.get("target_uuid")) : viewer.getUniqueId();
        List<MenuEntry> out = new ArrayList<>();
        for (MatchHistoryService.PlayerMatch line : history.recentFor(target, 45)) {
            MatchHistoryService.MatchRecord m = line.match();
            GameId game = GameId.of(m.gameId());
            Map<String, String> v = new HashMap<>();
            v.put("game", m.gameId());
            v.put("game_name", gameName(viewer, game));
            v.put("map_name", m.mapId() == null ? "-"
                    : core.maps().get(game, m.mapId()).map(MapDefinition::resolvedDisplayName).orElse(m.mapId()));
            v.put("result", line.result());
            v.put("result_label", ms.renderLegacyNoPrefix(locale, "core.history." + line.result(), Map.of()));
            v.put("kills", String.valueOf(line.kills()));
            v.put("deaths", String.valueOf(line.deaths()));
            v.put("assists", String.valueOf(line.assists()));
            v.put("team", line.team() == null ? "-" : line.team());
            v.put("duration", String.format(Locale.ROOT, "%d:%02d", m.durationSeconds() / 60, m.durationSeconds() % 60));
            v.put("when", ago(m.endedAt()));
            String icon = switch (line.result()) {
                case "win" -> "LIME_DYE";
                case "loss" -> "RED_DYE";
                default -> "GRAY_DYE";
            };
            out.add(new MenuEntry(v, me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr(icon, "PAPER", 1)));
        }
        return out;
    }

    static String ago(Instant when) {
        long s = Math.max(0, Duration.between(when, Instant.now()).getSeconds());
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            return s / 60 + "m";
        }
        if (s < 86400) {
            return s / 3600 + "h";
        }
        return s / 86400 + "d";
    }
}
