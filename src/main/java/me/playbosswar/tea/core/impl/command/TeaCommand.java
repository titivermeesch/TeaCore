package me.playbosswar.tea.core.impl.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TeaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TOP_LEVEL = List.of(
            "reload", "version", "help", "admin", "dashboard", "arena", "game", "map", "stats", "lobby", "waiting");

    private static final List<String> ARENA_SUBS = List.of(
            "list", "allocate", "join", "spectate", "observe", "leave", "release", "start");

    private final TeaCorePlugin plugin;
    private final TeaMapCommand mapCommand;

    public TeaCommand(TeaCorePlugin plugin) {
        this.plugin = plugin;
        this.mapCommand = new TeaMapCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> showHelp(sender);
            case "reload" -> handleReload(sender);
            case "version" -> handleVersion(sender);
            case "admin" -> handleAdmin(sender);
            case "dashboard" -> handleDashboard(sender, args);
            case "arena" -> handleArena(sender, args);
            case "game" -> handleGame(sender, args);
            case "map" -> mapCommand.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "stats" -> handleStats(sender, args);
            case "lobby" -> handleLobby(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "waiting" -> handleWaiting(sender, args);
            default -> plugin.messageService().sendToSender(sender, "core.command.unknown");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(TOP_LEVEL, args[0]);
        }
        if (args.length == 2 && "dashboard".equalsIgnoreCase(args[0])) {
            return filter(List.of("login"), args[1]);
        }
        if (args.length == 2 && "arena".equalsIgnoreCase(args[0])) {
            return filter(ARENA_SUBS, args[1]);
        }
        if (args.length == 3 && "arena".equalsIgnoreCase(args[0]) && "allocate".equalsIgnoreCase(args[1])) {
            return plugin.games().all().stream().map(d -> d.id().value()).filter(s -> s.startsWith(args[2])).toList();
        }
        if (args.length == 4 && "arena".equalsIgnoreCase(args[0]) && "allocate".equalsIgnoreCase(args[1])) {
            try {
                GameId g = GameId.of(args[2]);
                return plugin.maps().forGame(g).stream().map(m -> m.mapId())
                        .filter(s -> s.startsWith(args[3])).toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (args.length == 3 && ("join".equalsIgnoreCase(args[1]) || "spectate".equalsIgnoreCase(args[1])
                || "observe".equalsIgnoreCase(args[1])
                || "release".equalsIgnoreCase(args[1])
                || "start".equalsIgnoreCase(args[1])) && "arena".equalsIgnoreCase(args[0])) {
            return plugin.arenas().all().stream().map(a -> a.id().value().toString())
                    .filter(s -> s.startsWith(args[2])).toList();
        }
        if ("map".equalsIgnoreCase(args[0])) {
            return mapCommand.complete(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && "waiting".equalsIgnoreCase(args[0])) {
            return filter(List.of("set", "clear", "show", "room"), args[1]);
        }
        if (args.length >= 3 && "waiting".equalsIgnoreCase(args[0]) && "room".equalsIgnoreCase(args[1])) {
            var rooms = ((me.playbosswar.tea.core.impl.arena.ArenaManagerImpl) plugin.arenas()).waitingRooms();
            List<String> roomIds = rooms.all().stream().map(r -> r.id()).toList();
            List<String> games = plugin.games().all().stream().map(d -> d.id().value()).toList();
            if (args.length == 3) {
                return filter(List.of("edit", "pos1", "pos2", "spawn", "save", "discard", "assign", "unassign",
                        "list", "delete"), args[2]);
            }
            String sub = args[2].toLowerCase(Locale.ROOT);
            if (args.length == 4 && (sub.equals("edit") || sub.equals("delete") || sub.equals("assign"))) {
                return filter(roomIds, args[3]);
            }
            if ((args.length == 5 && sub.equals("assign")) || (args.length == 4 && sub.equals("unassign"))) {
                return filter(games, args[args.length - 1]);
            }
            if ((args.length == 6 && sub.equals("assign")) || (args.length == 5 && sub.equals("unassign"))) {
                try {
                    GameId g = GameId.of(args[args.length - 2]);
                    return filter(plugin.maps().forGame(g).stream().map(m -> m.mapId()).toList(), args[args.length - 1]);
                } catch (Exception ignored) {
                    return List.of();
                }
            }
            return List.of();
        }
        if (args.length == 3 && "waiting".equalsIgnoreCase(args[0])) {
            List<String> keys = new java.util.ArrayList<>(List.of("default"));
            plugin.games().all().forEach(d -> keys.add(d.id().value()));
            return filter(keys, args[2]);
        }
        if ("lobby".equalsIgnoreCase(args[0])) {
            var lobby = plugin.lobbyAdminCommand();
            if (lobby == null) return List.of();
            return lobby.complete(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!sender.hasPermission("tea.arena.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (!plugin.menus().open(player, "admin", Map.of())) {
            plugin.messageService().send(player, "core.arena.admin.menu-disabled");
        }
    }

    private void handleDashboard(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("login")) {
            plugin.messageService().sendToSender(sender, "core.dashboard.usage");
            return;
        }
        dashboardLogin(sender);
    }

    public void dashboardLogin(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!player.hasPermission(me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission.VIEW)) {
            plugin.messageService().send(player, "core.command.no-permission");
            return;
        }
        var codes = plugin.dashboardLoginCodes();
        String code = codes.newCode();
        java.util.List<String> perms = me.playbosswar.tea.core.impl.dashboard.auth.LoginCodes.granted(player::hasPermission);
        java.util.UUID uuid = player.getUniqueId();
        String name = player.getName();
        String url = plugin.dashboardConfig().publicUrl();
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            try {
                codes.store(code, uuid, name, perms);
                ok = true;
            } catch (RuntimeException e) {
                ok = false;
            }
            boolean stored = ok;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!stored) {
                    plugin.messageService().send(player, "core.dashboard.login.failed");
                    return;
                }
                if (url.isEmpty()) {
                    plugin.messageService().send(player, "core.dashboard.login.code", Map.of("code", code));
                    return;
                }
                String link = url + "/login?code=" + code;
                String label = plugin.messageService().renderPlainText(
                        plugin.messageService().resolveLocale(player), "core.dashboard.login.link-label", Map.of());
                plugin.messageService().send(player, "core.dashboard.login.link", Map.of("code", code));
                plugin.messageService().sendRaw(player, "<click:open_url:'" + link + "'><accent><u>" + label
                        + "</u></accent></click>");
            });
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("tea.reload")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        try {
            plugin.reload();
            plugin.shareLocalEdits();
            plugin.messageService().sendToSender(sender, "core.reload.success");
        } catch (Exception e) {
            plugin.messageService().sendToSender(sender, "core.reload.failed",
                    Map.of("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void handleVersion(CommandSender sender) {
        plugin.messageService().sendToSender(sender, "core.version.line",
                Map.of("version", plugin.version()));
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showArenaHelp(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "help" -> showArenaHelp(sender);
            case "list" -> arenaList(sender);
            case "allocate" -> arenaAllocate(sender, args);
            case "join" -> arenaJoin(sender, args, false);
            case "spectate" -> arenaJoin(sender, args, true);
            case "observe" -> arenaObserve(sender, args);
            case "leave" -> arenaLeave(sender);
            case "release" -> arenaRelease(sender, args);
            case "start" -> arenaStart(sender, args);
            default -> plugin.messageService().sendToSender(sender, "core.command.unknown");
        }
    }

    private void showArenaHelp(CommandSender sender) {
        MessageService ms = plugin.messageService();
        ms.sendToSender(sender, "core.arena.help.header");
        ms.sendToSender(sender, "core.arena.help.list");
        ms.sendToSender(sender, "core.arena.help.allocate");
        ms.sendToSender(sender, "core.arena.help.join");
        ms.sendToSender(sender, "core.arena.help.spectate");
        ms.sendToSender(sender, "core.arena.help.observe");
        ms.sendToSender(sender, "core.arena.help.leave");
        ms.sendToSender(sender, "core.arena.help.release");
        ms.sendToSender(sender, "core.arena.help.start");
    }

    private void arenaList(CommandSender sender) {
        MessageService ms = plugin.messageService();
        ms.sendToSender(sender, "core.arena.list.header");
        int shown = 0;
        for (var snap : plugin.coordination().allArenas()) {
            ms.sendToSender(sender, "core.arena.list.entry", Map.of(
                    "id", snap.arenaId().value().toString().substring(0, 8),
                    "server", snap.serverId(),
                    "game", snap.gameId().value(),
                    "map", snap.mapId() == null || snap.mapId().isEmpty() ? "-" : snap.mapId(),
                    "state", snap.state().name(),
                    "players", String.valueOf(snap.players()),
                    "max", String.valueOf(snap.maxPlayers())));
            shown++;
        }
        if (shown == 0) {
            ms.sendToSender(sender, "core.arena.list.empty");
        }
    }

    private void arenaAllocate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tea.arena.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.arena.allocate.usage");
            return;
        }
        try {
            GameId gameId = GameId.of(args[2]);
            String mapId = args.length >= 4 ? args[3] : null;
            Arena arena = plugin.arenas().allocate(gameId, mapId);
            plugin.messageService().sendToSender(sender, "core.arena.allocate.success", Map.of(
                    "id", arena.id().value().toString().substring(0, 8),
                    "game", gameId.value(),
                    "map", arena.mapId().orElse("-")));
        } catch (Exception e) {
            plugin.messageService().sendToSender(sender, "core.arena.allocate.failed",
                    Map.of("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void arenaJoin(CommandSender sender, String[] args, boolean spectator) {
        Player player;
        if (args.length >= 4 && (!(sender instanceof Player) || sender.hasPermission("tea.arena.admin"))) {
            player = org.bukkit.Bukkit.getPlayerExact(args[3]);
            if (player == null) {
                plugin.messageService().sendToSender(sender, "core.command.player-not-found", Map.of("player", args[3]));
                return;
            }
        } else if (sender instanceof Player self) {
            player = self;
        } else {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!player.hasPermission("tea.arena.play")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.arena.join.usage");
            return;
        }
        ArenaId arenaId = resolveArenaId(args[2]);
        if (arenaId == null) {
            plugin.messageService().sendToSender(sender, "core.arena.not-found",
                    Map.of("id", args[2]));
            return;
        }
        boolean ok = plugin.arenas().addPlayer(arenaId, player, spectator);
        if (!ok) {
            plugin.messageService().sendToSender(sender, "core.arena.join.failed");
        } else {
            plugin.messageService().sendToSender(player, "core.arena.join.success", Map.of(
                    "id", arenaId.value().toString().substring(0, 8)));
        }
    }

    private void arenaObserve(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!sender.hasPermission("tea.arena.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.arena.observe.usage");
            return;
        }
        ArenaId arenaId = resolveArenaId(args[2]);
        if (arenaId == null) {
            plugin.messageService().sendToSender(sender, "core.arena.not-found",
                    Map.of("id", args[2]));
            return;
        }
        boolean ok = plugin.arenas().observe(arenaId, player);
        if (ok) {
            plugin.messageService().send(player, "core.arena.observe.success",
                    Map.of("id", arenaId.value().toString().substring(0, 8)));
        } else {
            plugin.messageService().sendToSender(sender, "core.arena.not-found",
                    Map.of("id", args[2]));
        }
    }

    private void arenaLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!sender.hasPermission("tea.arena.play")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        boolean ok = plugin.arenas().removePlayer(player, ArenaPlayerLeaveEvent.LeaveReason.COMMAND);
        if (ok) {
            plugin.messageService().sendToSender(player, "core.arena.leave.success");
        } else {
            plugin.messageService().sendToSender(player, "core.arena.leave.not-in-arena");
        }
    }

    private void arenaStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tea.arena.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.arena.start.usage");
            return;
        }
        ArenaId arenaId = resolveArenaId(args[2]);
        if (arenaId == null) {
            plugin.messageService().sendToSender(sender, "core.arena.not-found",
                    Map.of("id", args[2]));
            return;
        }
        plugin.arenas().get(arenaId).ifPresentOrElse(arena -> {
            plugin.arenas().forceStart(arenaId);
            plugin.messageService().sendToSender(sender, "core.arena.start.done",
                    Map.of("id", arenaId.value().toString().substring(0, 8)));
        }, () -> plugin.messageService().sendToSender(sender, "core.arena.not-found",
                Map.of("id", args[2])));
    }

    private void arenaRelease(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tea.arena.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.arena.release.usage");
            return;
        }
        ArenaId arenaId = resolveArenaId(args[2]);
        if (arenaId == null) {
            plugin.messageService().sendToSender(sender, "core.arena.not-found",
                    Map.of("id", args[2]));
            return;
        }
        plugin.arenas().release(arenaId);
        plugin.messageService().sendToSender(sender, "core.arena.release.success",
                Map.of("id", arenaId.value().toString().substring(0, 8)));
    }

    private ArenaId resolveArenaId(String input) {
        for (Arena arena : plugin.arenas().all()) {
            String full = arena.id().value().toString();
            if (full.startsWith(input) || full.equalsIgnoreCase(input)) {
                return arena.id();
            }
        }
        try {
            return new ArenaId(UUID.fromString(input));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (!sender.hasPermission("tea.stats")) {
            ms.sendToSender(sender, "core.command.no-permission");
            return;
        }
        UUID targetUuid;
        String targetName;
        if (args.length >= 2) {
            if (!sender.hasPermission("tea.admin")) {
                ms.sendToSender(sender, "core.command.no-permission");
                return;
            }
            org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
            targetUuid = target.getUniqueId();
            targetName = target.getName() == null ? args[1] : target.getName();
        } else if (sender instanceof Player self) {
            targetUuid = self.getUniqueId();
            targetName = self.getName();
        } else {
            ms.sendToSender(sender, "core.stats.usage");
            return;
        }

        ms.sendToSender(sender, "core.stats.header", Map.of("player", targetName));
        int shown = 0;
        // Iterate persisted schemas so a lobby without in-process providers still shows
        // every game in the fleet.
        for (var schema : plugin.stats().allSchemas()) {
            Map<String, String> entries = plugin.stats().providerFor(schema).fetch(targetUuid);
            if (entries == null || entries.isEmpty())
                continue;
            ms.sendToSender(sender, "core.stats.game", Map.of("game", schema.gameId().value()));
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ms.sendToSender(sender, "core.stats.entry",
                        Map.of("label", entry.getKey(), "value", entry.getValue()));
            }
            shown++;
        }
        if (shown == 0) {
            ms.sendToSender(sender, "core.stats.empty");
        }
    }

    private void handleWaiting(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (!sender.hasPermission("tea.arena.admin")) {
            ms.sendToSender(sender, "core.command.no-permission");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        if (action.equals("room")) {
            handleWaitingRoom(sender, java.util.Arrays.copyOfRange(args, 2, args.length));
            return;
        }
        String key = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "default";
        var areas = ((me.playbosswar.tea.core.impl.arena.ArenaManagerImpl) plugin.arenas()).waitingAreas();
        switch (action) {
            case "set" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    ms.sendToSender(sender, "core.command.player-only");
                    return;
                }
                areas.set(key, player.getLocation());
                ms.sendToSender(sender, "core.waiting-area.set", Map.of("key", key,
                        "world", player.getWorld().getName()));
            }
            case "clear" -> ms.sendToSender(sender, areas.clear(key) ? "core.waiting-area.cleared"
                    : "core.waiting-area.none", Map.of("key", key));
            default -> {
                var at = key.equals("default")
                        ? plugin.getConfig().getConfigurationSection("waiting-area.default")
                        : plugin.getConfig().getConfigurationSection("waiting-area." + key);
                if (at == null) {
                    ms.sendToSender(sender, "core.waiting-area.none", Map.of("key", key));
                } else {
                    ms.sendToSender(sender, "core.waiting-area.show", Map.of("key", key,
                            "world", String.valueOf(at.getString("world")),
                            "x", String.valueOf(at.getDouble("x")), "y", String.valueOf(at.getDouble("y")),
                            "z", String.valueOf(at.getDouble("z"))));
                }
            }
        }
    }

    private void handleWaitingRoom(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        var rooms = ((me.playbosswar.tea.core.impl.arena.ArenaManagerImpl) plugin.arenas()).waitingRooms();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        org.bukkit.entity.Player player = sender instanceof org.bukkit.entity.Player p ? p : null;
        try {
            switch (sub) {
                case "edit", "create" -> {
                    if (player == null) { ms.sendToSender(sender, "core.command.player-only"); return; }
                    if (args.length < 2) { ms.sendToSender(sender, "core.waiting-room.usage"); return; }
                    if (plugin.arenas().arenaOf(player.getUniqueId()).isPresent()) {
                        ms.send(player, "core.map.leave-arena-first");
                        return;
                    }
                    String id = args[1].toLowerCase(Locale.ROOT);
                    boolean exists = rooms.get(id).isPresent();
                    rooms.edit(player, id);
                    ms.send(player, exists ? "core.waiting-room.editing" : "core.waiting-room.creating", Map.of("room", id));
                }
                case "pos1", "pos2" -> {
                    if (player == null) { ms.sendToSender(sender, "core.command.player-only"); return; }
                    rooms.setPos(player, sub.equals("pos1") ? 1 : 2);
                    ms.send(player, "core.waiting-room.pos-set", Map.of("which", sub));
                }
                case "spawn" -> {
                    if (player == null) { ms.sendToSender(sender, "core.command.player-only"); return; }
                    rooms.setSpawn(player);
                    ms.send(player, "core.waiting-room.spawn-set");
                }
                case "save" -> {
                    if (player == null) { ms.sendToSender(sender, "core.command.player-only"); return; }
                    var room = rooms.save(player);
                    ms.send(player, "core.waiting-room.saved", Map.of("room", room.id(),
                            "size", room.size()[0] + "x" + room.size()[1] + "x" + room.size()[2]));
                }
                case "discard" -> {
                    if (player == null) { ms.sendToSender(sender, "core.command.player-only"); return; }
                    rooms.discard(player);
                    ms.send(player, "core.waiting-room.discarded");
                }
                case "assign", "unassign" -> {
                    boolean assign = sub.equals("assign");
                    int need = assign ? 3 : 2;
                    if (args.length < need) { ms.sendToSender(sender, "core.waiting-room.usage"); return; }
                    String room = assign ? args[1].toLowerCase(Locale.ROOT) : null;
                    String game = args[assign ? 2 : 1].toLowerCase(Locale.ROOT);
                    String map = args.length > need ? args[need].toLowerCase(Locale.ROOT) : null;
                    if (room != null && rooms.get(room).isEmpty()) {
                        ms.sendToSender(sender, "core.waiting-room.unknown", Map.of("room", room));
                        return;
                    }
                    rooms.assign(GameId.of(game), map, room);
                    String target = map == null ? game : game + "/" + map;
                    ms.sendToSender(sender, assign ? "core.waiting-room.assigned" : "core.waiting-room.unassigned",
                            Map.of("room", room == null ? "" : room, "target", target));
                }
                case "delete" -> {
                    if (args.length < 2) { ms.sendToSender(sender, "core.waiting-room.usage"); return; }
                    String id = args[1].toLowerCase(Locale.ROOT);
                    ms.sendToSender(sender, rooms.delete(id) ? "core.waiting-room.deleted" : "core.waiting-room.unknown",
                            Map.of("room", id));
                }
                case "list" -> {
                    if (rooms.all().isEmpty()) {
                        ms.sendToSender(sender, "core.waiting-room.none");
                        return;
                    }
                    for (var room : rooms.all()) {
                        java.util.List<String> used = new java.util.ArrayList<>();
                        rooms.gameAssignments().forEach((g, r) -> { if (r.equals(room.id())) used.add(g); });
                        rooms.mapAssignments().forEach((g, m) -> m.forEach((map, r) -> {
                            if (r.equals(room.id())) used.add(g + "/" + map);
                        }));
                        ms.sendToSender(sender, "core.waiting-room.list-entry", Map.of("room", room.id(),
                                "size", room.size()[0] + "x" + room.size()[1] + "x" + room.size()[2],
                                "used", used.isEmpty() ? "-" : String.join(", ", used)));
                    }
                }
                default -> ms.sendToSender(sender, "core.waiting-room.usage");
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            ms.sendToSender(sender, "core.waiting-room.failed", Map.of("reason", String.valueOf(e.getMessage())));
        } catch (java.io.IOException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Waiting room command failed", e);
            ms.sendToSender(sender, "core.waiting-room.failed", Map.of("reason", String.valueOf(e.getMessage())));
        }
    }

    private void handleLobby(CommandSender sender, String[] args) {
        var lobby = plugin.lobbyAdminCommand();
        if (lobby == null) {
            plugin.messageService().sendToSender(sender, "core.command.lobby-disabled");
            return;
        }
        lobby.handle(sender, args);
    }

    private void handleGame(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            ms.sendToSender(sender, "core.game.list.header");
            // Use every game the fleet publishes as hosted: the lobby loads no game plugins
            // but /tea game list should still show the games admins can /play to.
            java.util.LinkedHashSet<me.playbosswar.tea.core.api.game.GameId> ids =
                    new java.util.LinkedHashSet<>();
            for (GameDefinition def : plugin.games().all())
                ids.add(def.id());
            ids.addAll(plugin.coordination().allHostedGames());
            int shown = 0;
            for (var id : ids) {
                GameDefinition def = plugin.games().get(id).orElse(null);
                int maps = plugin.maps().forGame(id).size();
                String teams = def == null ? "remote" : def.teamMode().name();
                ms.sendToSender(sender, "core.game.list.entry", Map.of(
                        "id", id.value(),
                        "maps", String.valueOf(maps),
                        "teams", teams));
                shown++;
            }
            if (shown == 0) {
                ms.sendToSender(sender, "core.game.list.empty");
            }
            return;
        }
        ms.sendToSender(sender, "core.command.unknown");
    }

    private void showHelp(CommandSender sender) {
        MessageService ms = plugin.messageService();
        ms.sendToSender(sender, "core.help.header");
        ms.sendToSender(sender, "core.help.reload");
        ms.sendToSender(sender, "core.help.version");
        ms.sendToSender(sender, "core.help.admin");
        ms.sendToSender(sender, "core.help.dashboard");
        ms.sendToSender(sender, "core.help.arena");
        ms.sendToSender(sender, "core.help.game");
        ms.sendToSender(sender, "core.help.map");
        ms.sendToSender(sender, "core.help.stats");
        ms.sendToSender(sender, "core.help.hint-arena");
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(p))
                out.add(opt);
        }
        return out;
    }
}
