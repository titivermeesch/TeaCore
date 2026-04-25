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
            "reload", "version", "help", "arena", "game", "map", "stats", "economy");

    private static final List<String> ARENA_SUBS = List.of(
            "list", "allocate", "join", "spectate", "observe", "leave", "release", "start");

    private static final List<String> ECONOMY_SUBS = List.of(
            "balance", "give", "take", "set");

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
            case "arena" -> handleArena(sender, args);
            case "game" -> handleGame(sender, args);
            case "map" -> mapCommand.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "stats" -> handleStats(sender, args);
            case "economy" -> handleEconomy(sender, args);
            default -> plugin.messageService().sendToSender(sender, "core.command.unknown");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(TOP_LEVEL, args[0]);
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
        if ("economy".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return filter(ECONOMY_SUBS, args[1]);
            if (args.length == 3 && !"balance".equalsIgnoreCase(args[1])) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getName)
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .toList();
            }
        }
        return List.of();
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("tea.reload")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        try {
            plugin.reload();
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
        // Fleet-wide view via the coordination backend; local-only view if someone
        // prefers `/tea arena list local` could be a future enhancement.
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
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!sender.hasPermission("tea.arena.play")) {
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
            // Looking up someone else requires admin.
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
        for (var provider : plugin.stats().all()) {
            Map<String, String> entries = provider.fetch(targetUuid);
            if (entries == null || entries.isEmpty())
                continue;
            ms.sendToSender(sender, "core.stats.game", Map.of("game", provider.gameId().value()));
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

    private void handleEconomy(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        var economy = plugin.economy();
        if (economy == null || !economy.isAvailable()) {
            ms.sendToSender(sender, "core.economy.unavailable");
            return;
        }
        if (args.length < 2) {
            ms.sendToSender(sender, "core.economy.usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "balance" -> economyBalance(sender, args);
            case "give" -> economyAdjust(sender, args, EconomyOp.GIVE);
            case "take" -> economyAdjust(sender, args, EconomyOp.TAKE);
            case "set" -> economyAdjust(sender, args, EconomyOp.SET);
            default -> ms.sendToSender(sender, "core.economy.usage");
        }
    }

    private void economyBalance(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        var economy = plugin.economy();
        UUID targetUuid;
        String targetName;
        if (args.length >= 3) {
            if (!sender.hasPermission("tea.economy.admin")) {
                ms.sendToSender(sender, "core.command.no-permission");
                return;
            }
            org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[2]);
            targetUuid = target.getUniqueId();
            targetName = target.getName() == null ? args[2] : target.getName();
        } else if (sender instanceof Player self) {
            if (!sender.hasPermission("tea.economy.balance")) {
                ms.sendToSender(sender, "core.command.no-permission");
                return;
            }
            targetUuid = self.getUniqueId();
            targetName = self.getName();
        } else {
            ms.sendToSender(sender, "core.economy.balance.usage");
            return;
        }
        double balance = economy.balance(targetUuid);
        ms.sendToSender(sender, "core.economy.balance.result", Map.of(
                "player", targetName,
                "amount", economy.format(balance)));
    }

    private enum EconomyOp { GIVE, TAKE, SET }

    private void economyAdjust(CommandSender sender, String[] args, EconomyOp op) {
        MessageService ms = plugin.messageService();
        if (!sender.hasPermission("tea.economy.admin")) {
            ms.sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 4) {
            ms.sendToSender(sender, "core.economy." + op.name().toLowerCase(Locale.ROOT) + ".usage");
            return;
        }
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[2]);
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            ms.sendToSender(sender, "core.economy.invalid-amount", Map.of("value", args[3]));
            return;
        }
        if (amount < 0) {
            ms.sendToSender(sender, "core.economy.invalid-amount", Map.of("value", args[3]));
            return;
        }
        var economy = plugin.economy();
        boolean ok = switch (op) {
            case GIVE -> economy.deposit(target.getUniqueId(), amount);
            case TAKE -> economy.withdraw(target.getUniqueId(), amount);
            case SET -> economy.set(target.getUniqueId(), amount);
        };
        String targetName = target.getName() == null ? args[2] : target.getName();
        if (ok) {
            ms.sendToSender(sender, "core.economy." + op.name().toLowerCase(Locale.ROOT) + ".success",
                    Map.of(
                            "player", targetName,
                            "amount", economy.format(amount),
                            "balance", economy.format(economy.balance(target.getUniqueId()))));
        } else {
            ms.sendToSender(sender, "core.economy.operation-failed",
                    Map.of("player", targetName));
        }
    }

    private void handleGame(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            ms.sendToSender(sender, "core.game.list.header");
            int shown = 0;
            for (GameDefinition def : plugin.games().all()) {
                int maps = plugin.maps().forGame(def.id()).size();
                ms.sendToSender(sender, "core.game.list.entry", Map.of(
                        "id", def.id().value(),
                        "maps", String.valueOf(maps),
                        "teams", def.teamMode().name()));
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
