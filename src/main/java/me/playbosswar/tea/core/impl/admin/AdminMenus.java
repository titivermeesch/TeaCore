package me.playbosswar.tea.core.impl.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.menu.MenuClick;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.menu.GameDisplay;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Arena ids may be the full id or the short 8-character form. */
public final class AdminMenus {

    private static final String ARENA_PERMISSION = "tea.arena.admin";
    private static final String MAP_PERMISSION = "tea.map.admin";

    private final TeaCorePlugin plugin;
    private final ArenaAdmin admin;
    private final GameDisplay display;

    public AdminMenus(TeaCorePlugin plugin, ArenaAdmin admin, GameDisplay display) {
        this.plugin = plugin;
        this.admin = admin;
        this.display = display;
    }

    public void register(MenuService menus) {
        menus.registerSource("admin-games", (viewer, args) -> games(viewer));
        menus.registerSource("admin-arenas", this::arenas);
        menus.registerSource("admin-arena", this::arena);
        menus.registerSource("admin-players", this::players);
        menus.registerSource("admin-winners", this::winners);
        menus.registerSource("admin-move-targets", this::moveTargets);
        menus.registerSource("admin-maps", this::maps);
        menus.registerSource("admin-map-versions", this::mapVersions);

        arenaAction(menus, "admin-start", (click, s, rest) -> {
            admin.start(s);
            staff(click, "skipped " + label(s) + " to its next phase");
            done(click, "core.arena.admin.started", s);
        });
        arenaAction(menus, "admin-release", (click, s, rest) -> {
            admin.release(s);
            staff(click, "released " + label(s));
            done(click, "core.arena.admin.released", s);
            click.player().closeInventory();
            open(click.player(), "admin-arenas", Map.of());
        });
        arenaAction(menus, "admin-end", (click, s, rest) -> {
            String kind = rest.length > 0 ? rest[0] : "draw";
            String id = rest.length > 1 ? rest[1] : null;
            if (s.state() != ArenaState.LIVE) {
                send(click.player(), "core.arena.admin.not-live", Map.of("id", shortId(s)));
                return;
            }
            admin.end(s, kind.equals("team") ? id : null, kind.equals("player") ? id : null);
            staff(click, "ended the match in " + label(s) + (kind.equals("draw") ? " as a draw"
                    : kind.equals("team") ? " (team " + id + " wins)" : " (" + nameOf(s, id) + " wins)"));
            done(click, "core.arena.admin.ended", s);
            open(click.player(), "admin-arena", Map.of("arena", s.arenaId().value().toString()));
        });
        arenaAction(menus, "admin-visit", (click, s, rest) -> {
            click.player().closeInventory();
            admin.visit(click.player(), s, rest.length > 0 && rest[0].equals("spectate") ? "spectate" : "observe");
        });
        arenaAction(menus, "admin-kick", (click, s, rest) -> withPlayer(click, s, rest, (player, name) -> {
            admin.kick(s, player);
            staff(click, "kicked " + name + " from " + label(s));
            send(click.player(), "core.arena.admin.kicked", Map.of("player", name, "id", shortId(s)));
            click.refresh();
        }));
        arenaAction(menus, "admin-spectator", (click, s, rest) -> withPlayer(click, s, rest, (player, name) -> {
            admin.makeSpectator(s, player);
            staff(click, "made " + name + " a spectator in " + label(s));
            send(click.player(), "core.arena.admin.made-spectator", Map.of("player", name));
            click.refresh();
        }));
        arenaAction(menus, "admin-tp", (click, s, rest) -> withPlayer(click, s, rest, (player, name) -> {
            click.player().closeInventory();
            admin.visit(click.player(), s, "tp:" + player);
        }));
        arenaAction(menus, "admin-move", (click, s, rest) -> withPlayer(click, s, rest, (player, name) -> {
            if (rest.length < 2) {
                return;
            }
            ArenaSnapshot to = admin.find(rest[1]).orElse(null);
            if (to == null) {
                send(click.player(), "core.arena.not-found", Map.of("id", rest[1]));
                return;
            }
            admin.move(s, player, to);
            staff(click, "moved " + name + " from " + label(s) + " to " + label(to));
            send(click.player(), "core.arena.admin.moved", Map.of("player", name, "id", shortId(to)));
            open(click.player(), "admin-players", Map.of("arena", s.arenaId().value().toString()));
        }));

        menus.registerAction("admin-allocate", (click, arg) -> {
            if (!allowed(click.player(), ARENA_PERMISSION)) {
                return;
            }
            String[] parts = arg.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) {
                return;
            }
            GameId game = GameId.of(parts[0]);
            String map = parts.length > 1 ? parts[1] : null;
            if (admin.allocate(game, map)) {
                staff(click, "allocated a " + game.value() + " arena" + (map == null ? "" : " on " + map));
                send(click.player(), "core.arena.admin.allocated", Map.of(
                        "game", game.value(), "map", map == null ? "-" : map));
            } else {
                send(click.player(), "core.arena.admin.no-host", Map.of("game", game.value()));
            }
        });
        menus.registerAction("admin-map-edit", (click, arg) -> {
            if (allowed(click.player(), MAP_PERMISSION)) {
                click.player().closeInventory();
                click.player().performCommand("tea map edit " + arg.trim());
            }
        });
        menus.registerAction("admin-map-delete", (click, arg) -> {
            if (allowed(click.player(), MAP_PERMISSION)) {
                click.player().performCommand("tea map delete " + arg.trim());
                staff(click, "deleted map " + arg.trim().replace(' ', '/'));
                click.refresh();
            }
        });
        menus.registerAction("admin-map-restore", (click, arg) -> {
            if (allowed(click.player(), MAP_PERMISSION)) {
                // The command reports the result and posts the staff action.
                click.player().performCommand("tea map restore " + arg.trim());
                click.refresh();
            }
        });
        menus.registerAction("admin-reload-maps", (click, arg) -> {
            if (allowed(click.player(), MAP_PERMISSION)) {
                click.player().performCommand("tea map reload");
            }
        });
    }

    private interface ArenaAction {
        void run(MenuClick click, ArenaSnapshot arena, String[] rest);
    }

    private interface PlayerAction {
        void run(UUID player, String name);
    }

    private void arenaAction(MenuService menus, String id, ArenaAction action) {
        menus.registerAction(id, (click, arg) -> {
            if (!allowed(click.player(), ARENA_PERMISSION)) {
                return;
            }
            String[] parts = arg.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) {
                return;
            }
            ArenaSnapshot s = admin.find(parts[0]).orElse(null);
            if (s == null) {
                send(click.player(), "core.arena.not-found", Map.of("id", parts[0]));
                return;
            }
            String[] rest = new String[parts.length - 1];
            System.arraycopy(parts, 1, rest, 0, rest.length);
            action.run(click, s, rest);
        });
    }

    private void withPlayer(MenuClick click, ArenaSnapshot s, String[] rest, PlayerAction action) {
        if (rest.length == 0) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(rest[0]);
        } catch (IllegalArgumentException e) {
            return;
        }
        String name = s.roster().stream().filter(r -> r.uuid().equals(uuid)).map(ArenaSnapshot.RosterEntry::name)
                .findFirst().orElse(uuid.toString().substring(0, 8));
        action.run(uuid, name);
    }

    private void staff(MenuClick click, String action) {
        if (plugin.discordEvents() != null) {
            plugin.discordEvents().staff(click.player().getName(), action);
        }
    }

    private static String label(ArenaSnapshot s) {
        return s.gameId().value() + " #" + shortId(s) + " (" + s.serverId() + ")";
    }

    private static String nameOf(ArenaSnapshot s, String uuid) {
        return s.roster().stream().filter(r -> r.uuid().toString().equals(uuid)).map(ArenaSnapshot.RosterEntry::name)
                .findFirst().orElse(uuid == null ? "?" : uuid);
    }

    private boolean allowed(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        send(player, "core.command.no-permission", Map.of());
        return false;
    }

    private void done(MenuClick click, String key, ArenaSnapshot s) {
        send(click.player(), key, Map.of("id", shortId(s), "server", s.serverId()));
        click.refresh();
    }

    private void send(Player player, String key, Map<String, String> values) {
        plugin.messageService().send(player, key, values);
    }

    private void open(Player player, String menu, Map<String, String> args) {
        plugin.menus().open(player, menu, args);
    }

    private Set<GameId> allGames() {
        Set<GameId> ids = new LinkedHashSet<>();
        plugin.games().all().forEach(def -> ids.add(def.id()));
        try {
            ids.addAll(plugin.coordination().allHostedGames());
        } catch (RuntimeException ignored) {
            // standalone backends may not track hosted games
        }
        for (ArenaSnapshot s : plugin.coordination().allArenas()) {
            ids.add(s.gameId());
        }
        return ids;
    }

    private List<MenuEntry> games(Player viewer) {
        String locale = plugin.messageService().resolveLocale(viewer);
        List<ArenaSnapshot> arenas = List.copyOf(plugin.coordination().allArenas());
        List<MenuEntry> out = new ArrayList<>();
        for (GameId game : allGames()) {
            int count = 0;
            int players = 0;
            int ready = 0;
            int running = 0;
            for (ArenaSnapshot a : arenas) {
                if (a.gameId().equals(game)) {
                    count++;
                    players += a.players();
                    switch (a.state()) {
                        case ALLOCATING, WAITING, COUNTDOWN -> {
                            if (a.maxPlayers() <= 0 || a.players() < a.maxPlayers()) ready++;
                        }
                        case STARTING, LIVE, ENDING -> running++;
                        default -> { }
                    }
                }
            }
            int queued = plugin.coordination().queueFor(game).size();
            Map<String, String> v = new HashMap<>();
            v.put("game_id", game.value());
            v.put("game_name", display.name(locale, game));
            v.put("arenas", String.valueOf(count));
            v.put("players", String.valueOf(players));
            v.put("ready", String.valueOf(ready));
            v.put("running", String.valueOf(running));
            v.put("queued", String.valueOf(queued));
            v.put("maps", String.valueOf(plugin.maps().forGame(game).size()));
            v.put("local", String.valueOf(plugin.games().get(game).isPresent()));
            out.add(new MenuEntry(v, new ItemStack(display.icon(game))));
        }
        return out;
    }

    private List<MenuEntry> arenas(Player viewer, Map<String, String> args) {
        String game = args.get("game");
        List<ArenaSnapshot> all = new ArrayList<>(plugin.coordination().allArenas());
        all.sort((a, b) -> {
            int byGame = a.gameId().value().compareTo(b.gameId().value());
            return byGame != 0 ? byGame : a.arenaId().value().compareTo(b.arenaId().value());
        });
        List<MenuEntry> out = new ArrayList<>();
        for (ArenaSnapshot s : all) {
            if (game != null && !game.isBlank() && !s.gameId().value().equals(game)) {
                continue;
            }
            out.add(new MenuEntry(arenaValues(viewer, s), stateIcon(s.state())));
        }
        return out;
    }

    private List<MenuEntry> arena(Player viewer, Map<String, String> args) {
        String id = args.get("arena");
        if (id == null) {
            return List.of();
        }
        return admin.find(id)
                .map(s -> List.of(new MenuEntry(arenaValues(viewer, s), stateIcon(s.state()))))
                .orElse(List.of());
    }

    Map<String, String> arenaValues(Player viewer, ArenaSnapshot s) {
        String locale = plugin.messageService().resolveLocale(viewer);
        MessageService ms = plugin.messageService();
        Map<String, String> v = new HashMap<>();
        v.put("arena", s.arenaId().value().toString());
        v.put("arena_id", shortId(s));
        v.put("game", s.gameId().value());
        v.put("game_name", display.name(locale, s.gameId()));
        v.put("map_id", s.mapId() == null || s.mapId().isEmpty() ? "-" : s.mapId());
        v.put("map_name", mapName(s));
        v.put("state", s.state().name().toLowerCase(Locale.ROOT));
        v.put("state_label", ms.renderLegacyNoPrefix(locale,
                "core.arena.admin.state." + s.state().name().toLowerCase(Locale.ROOT), Map.of()));
        v.put("players", String.valueOf(s.players()));
        v.put("max", String.valueOf(s.maxPlayers()));
        v.put("min", String.valueOf(s.minPlayers()));
        long spectators = s.roster().stream().filter(ArenaSnapshot.RosterEntry::spectator).count();
        v.put("spectators", String.valueOf(spectators));
        v.put("server", s.serverId());
        v.put("local", String.valueOf(admin.isLocal(s)));
        v.put("time", s.secondsRemaining() < 0 ? "-"
                : String.format(Locale.ROOT, "%d:%02d", s.secondsRemaining() / 60, s.secondsRemaining() % 60));
        v.put("live", String.valueOf(s.state() == ArenaState.LIVE));
        return v;
    }

    private List<MenuEntry> players(Player viewer, Map<String, String> args) {
        String id = args.get("arena");
        ArenaSnapshot s = id == null ? null : admin.find(id).orElse(null);
        if (s == null) {
            return List.of();
        }
        String locale = plugin.messageService().resolveLocale(viewer);
        List<MenuEntry> out = new ArrayList<>();
        List<ArenaSnapshot.RosterEntry> roster = new ArrayList<>(s.roster());
        roster.sort((a, b) -> Boolean.compare(a.spectator(), b.spectator()) != 0
                ? Boolean.compare(a.spectator(), b.spectator())
                : a.name().compareToIgnoreCase(b.name()));
        for (ArenaSnapshot.RosterEntry r : roster) {
            Map<String, String> v = new HashMap<>(arenaValues(viewer, s));
            v.put("player", r.uuid().toString());
            v.put("name", r.name());
            v.put("team", r.team() == null ? "-" : r.team());
            v.put("spectator", String.valueOf(r.spectator()));
            v.put("role", plugin.messageService().renderLegacyNoPrefix(locale,
                    r.spectator() ? "core.arena.admin.role.spectator" : "core.arena.admin.role.player", Map.of()));
            out.add(new MenuEntry(v, me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr(r.spectator() ? "SKELETON_SKULL" : "PLAYER_HEAD", "PAPER", 1)));
        }
        return out;
    }

    private List<MenuEntry> winners(Player viewer, Map<String, String> args) {
        String id = args.get("arena");
        ArenaSnapshot s = id == null ? null : admin.find(id).orElse(null);
        if (s == null) {
            return List.of();
        }
        List<MenuEntry> out = new ArrayList<>();
        Map<String, Integer> teams = new LinkedHashMap<>();
        for (ArenaSnapshot.RosterEntry r : s.roster()) {
            if (!r.spectator() && r.team() != null) {
                teams.merge(r.team(), 1, Integer::sum);
            }
        }
        if (!teams.isEmpty()) {
            for (Map.Entry<String, Integer> t : teams.entrySet()) {
                Map<String, String> v = new HashMap<>(arenaValues(viewer, s));
                v.put("kind", "team");
                v.put("winner", t.getKey());
                v.put("name", capitalize(t.getKey()));
                v.put("detail", t.getValue() + " player(s)");
                out.add(new MenuEntry(v, teamBanner(t.getKey())));
            }
            return out;
        }
        for (ArenaSnapshot.RosterEntry r : s.roster()) {
            if (r.spectator()) {
                continue;
            }
            Map<String, String> v = new HashMap<>(arenaValues(viewer, s));
            v.put("kind", "player");
            v.put("winner", r.uuid().toString());
            v.put("name", r.name());
            v.put("detail", "");
            out.add(new MenuEntry(v, me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr("PLAYER_HEAD", "PAPER", 1)));
        }
        return out;
    }

    private List<MenuEntry> moveTargets(Player viewer, Map<String, String> args) {
        String from = args.get("arena");
        String player = args.get("player");
        ArenaSnapshot current = from == null ? null : admin.find(from).orElse(null);
        List<MenuEntry> out = new ArrayList<>();
        for (ArenaSnapshot s : plugin.coordination().allArenas()) {
            if (current != null && s.arenaId().equals(current.arenaId())) {
                continue;
            }
            if (s.state() == ArenaState.RELEASED || s.players() >= s.maxPlayers()) {
                continue;
            }
            Map<String, String> v = arenaValues(viewer, s);
            v.put("from", current == null ? "" : current.arenaId().value().toString());
            v.put("player", player == null ? "" : player);
            out.add(new MenuEntry(v, stateIcon(s.state())));
        }
        return out;
    }

    private List<MenuEntry> maps(Player viewer, Map<String, String> args) {
        String gameArg = args.get("game");
        if (gameArg == null || gameArg.isBlank()) {
            return List.of();
        }
        GameId game = GameId.of(gameArg);
        String locale = plugin.messageService().resolveLocale(viewer);
        List<ArenaSnapshot> arenas = plugin.coordination().allArenas().stream()
                .filter(a -> a.gameId().equals(game)).toList();
        List<MenuEntry> out = new ArrayList<>();
        for (MapDefinition map : plugin.maps().forGame(game)) {
            long running = arenas.stream().filter(a -> map.mapId().equals(a.mapId())).count();
            Map<String, String> v = new HashMap<>();
            v.put("game", game.value());
            v.put("game_name", display.name(locale, game));
            v.put("map_id", map.mapId());
            v.put("map_name", map.resolvedDisplayName());
            v.put("spawns", String.valueOf(map.participantSpawns().size()));
            v.put("min", String.valueOf(map.minPlayers()));
            v.put("max", String.valueOf(map.maxPlayers()));
            v.put("points", String.valueOf(map.extensions().size()));
            v.put("arenas", String.valueOf(running));
            v.put("editable", String.valueOf(plugin.games().get(game).isPresent()));
            out.add(new MenuEntry(v, me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr("FILLED_MAP", "PAPER", 1)));
        }
        return out;
    }

    private List<MenuEntry> mapVersions(Player viewer, Map<String, String> args) {
        String gameArg = args.get("game");
        String mapArg = args.get("map");
        if (gameArg == null || gameArg.isBlank() || mapArg == null || mapArg.isBlank()) {
            return List.of();
        }
        GameId game = GameId.of(gameArg);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());
        List<MenuEntry> out = new ArrayList<>();
        List<me.playbosswar.tea.core.impl.map.MapBackups.Version> versions;
        try {
            versions = plugin.mapEditingService().backups().list(game, mapArg);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        for (var v : versions) {
            Map<String, String> values = new HashMap<>();
            values.put("game", game.value());
            values.put("map", mapArg);
            values.put("n", String.valueOf(v.number()));
            values.put("when", fmt.format(v.when()));
            values.put("actor", v.actor());
            values.put("reason", v.reason());
            out.add(new MenuEntry(values, new ItemStack(Material.PAPER)));
        }
        return out;
    }

    private String mapName(ArenaSnapshot s) {
        if (s.mapId() == null || s.mapId().isEmpty()) {
            return "-";
        }
        return plugin.maps().get(s.gameId(), s.mapId()).map(MapDefinition::resolvedDisplayName).orElse(s.mapId());
    }

    static String shortId(ArenaSnapshot s) {
        return s.arenaId().value().toString().substring(0, 8);
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

    private static ItemStack teamBanner(String team) {
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr(team.toUpperCase(Locale.ROOT) + "_BANNER", "WHITE_BANNER", 1);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
