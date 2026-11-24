package me.playbosswar.tea.core.impl.lobby.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.lobby.sign.ConnectionSign;
import me.playbosswar.tea.core.impl.lobby.sign.ConnectionSignService;
import me.playbosswar.tea.core.impl.lobby.spawn.LobbySpawnService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TeaLobbyCommand implements TabExecutor {

    private static final List<String> TOP = List.of("sign", "spawn", "config", "reload");
    private static final List<String> SIGN_SUBS = List.of("bind", "unbind", "list");
    private static final List<String> SPAWN_SUBS = List.of("set", "tp");
    private static final List<String> CONFIG_SUBS = List.of("show", "manage-spawn", "gamemode");
    private static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    private static final List<String> GAMEMODES = List.of("survival", "creative", "adventure", "spectator");

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final ConnectionSignService signs;
    private final LobbySpawnService spawnService;

    public TeaLobbyCommand(Plugin plugin, TeaCoreAPI core, ConnectionSignService signs,
            LobbySpawnService spawnService) {
        this.plugin = plugin;
        this.core = core;
        this.signs = signs;
        this.spawnService = spawnService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        handle(sender, args);
        return true;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tea.lobby.admin")) {
            core.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length == 0) {
            core.messageService().sendToSender(sender, "lobby.admin.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "sign" -> handleSign(sender, Arrays.copyOfRange(args, 1, args.length));
            case "spawn" -> handleSpawn(sender, Arrays.copyOfRange(args, 1, args.length));
            case "config" -> handleConfig(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> core.messageService().sendToSender(sender, "core.command.unknown");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(sender, args);
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return TOP.stream().filter(s -> s.startsWith(p)).toList();
        }
        if (args.length == 2 && "sign".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return SIGN_SUBS.stream().filter(s -> s.startsWith(p)).toList();
        }
        if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return SPAWN_SUBS.stream().filter(s -> s.startsWith(p)).toList();
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return CONFIG_SUBS.stream().filter(s -> s.startsWith(p)).toList();
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0])) {
            String p = args[2].toLowerCase(Locale.ROOT);
            if ("manage-spawn".equalsIgnoreCase(args[1])) {
                return BOOLEAN_VALUES.stream().filter(s -> s.startsWith(p)).toList();
            }
            if ("gamemode".equalsIgnoreCase(args[1])) {
                return GAMEMODES.stream().filter(s -> s.startsWith(p)).toList();
            }
        }
        if (args.length == 3 && "sign".equalsIgnoreCase(args[0]) && "bind".equalsIgnoreCase(args[1])) {
            String p = args[2].toLowerCase(Locale.ROOT);
            // Fleet-wide view; the lobby doesn't load game plugins itself.
            return core.coordination().allHostedGames().stream().map(GameId::value)
                    .filter(s -> s.startsWith(p)).toList();
        }
        if (args.length == 4 && "sign".equalsIgnoreCase(args[0]) && "bind".equalsIgnoreCase(args[1])) {
            try {
                GameId g = GameId.of(args[2]);
                String p = args[3].toLowerCase(Locale.ROOT);
                return core.maps().forGame(g).stream().map(m -> m.mapId())
                        .filter(s -> s.startsWith(p)).toList();
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private void handleReload(CommandSender sender) {
        signs.loadAll();
        spawnService.reload();
        core.messageService().sendToSender(sender, "lobby.admin.reload.done",
                Map.of("count", String.valueOf(signs.all().size())));
    }

    private void handleSign(CommandSender sender, String[] args) {
        if (args.length == 0) {
            core.messageService().sendToSender(sender, "lobby.admin.sign.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "bind" -> handleSignBind(sender, args);
            case "unbind" -> handleSignUnbind(sender);
            case "list" -> handleSignList(sender);
            default -> core.messageService().sendToSender(sender, "core.command.unknown");
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (args.length == 0) {
            core.messageService().sendToSender(sender, "lobby.admin.spawn.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSpawnSet(sender);
            case "tp" -> handleSpawnTp(sender);
            default -> core.messageService().sendToSender(sender, "core.command.unknown");
        }
    }

    private void handleSpawnSet(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        spawnService.setSpawn(player);
        Location loc = player.getLocation();
        core.messageService().send(player, "lobby.admin.spawn.set.done", Map.of(
                "world", loc.getWorld().getName(),
                "x", String.format(Locale.ROOT, "%.1f", loc.getX()),
                "y", String.format(Locale.ROOT, "%.1f", loc.getY()),
                "z", String.format(Locale.ROOT, "%.1f", loc.getZ())));
    }

    private void handleConfig(CommandSender sender, String[] args) {
        if (args.length == 0) {
            core.messageService().sendToSender(sender, "lobby.admin.config.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "show" -> handleConfigShow(sender);
            case "manage-spawn" -> handleConfigManageSpawn(sender, args);
            case "gamemode" -> handleConfigGameMode(sender, args);
            default -> core.messageService().sendToSender(sender, "core.command.unknown");
        }
    }

    private void handleConfigShow(CommandSender sender) {
        Location loc = spawnService.spawnLocation();
        String spawnLabel = loc == null
                ? "(not set)"
                : loc.getWorld().getName() + " @ "
                        + String.format(Locale.ROOT, "%.1f,%.1f,%.1f",
                                loc.getX(), loc.getY(), loc.getZ());
        core.messageService().sendToSender(sender, "lobby.admin.config.show", Map.of(
                "manage-spawn", String.valueOf(spawnService.manageSpawn()),
                "gamemode", spawnService.gameMode().name(),
                "spawn", spawnLabel));
    }

    private void handleConfigManageSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.messageService().sendToSender(sender, "lobby.admin.config.manage-spawn.usage");
            return;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        boolean value;
        if ("true".equals(raw) || "on".equals(raw) || "enable".equals(raw)) {
            value = true;
        } else if ("false".equals(raw) || "off".equals(raw) || "disable".equals(raw)) {
            value = false;
        } else {
            core.messageService().sendToSender(sender, "lobby.admin.config.manage-spawn.usage");
            return;
        }
        spawnService.setManageSpawn(value);
        core.messageService().sendToSender(sender, "lobby.admin.config.manage-spawn.done",
                Map.of("value", String.valueOf(value)));
    }

    private void handleConfigGameMode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.messageService().sendToSender(sender, "lobby.admin.config.gamemode.usage");
            return;
        }
        GameMode mode;
        try {
            mode = GameMode.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            core.messageService().sendToSender(sender, "lobby.admin.config.gamemode.invalid",
                    Map.of("value", args[1]));
            return;
        }
        spawnService.setGameMode(mode);
        core.messageService().sendToSender(sender, "lobby.admin.config.gamemode.done",
                Map.of("mode", mode.name()));
    }

    private void handleSpawnTp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        Location loc = spawnService.spawnLocation();
        if (loc == null) {
            core.messageService().send(player, "lobby.admin.spawn.not-set");
            return;
        }
        player.teleport(loc);
        core.messageService().send(player, "lobby.admin.spawn.tp.done");
    }

    private void handleSignBind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (args.length < 2) {
            core.messageService().send(player, "lobby.admin.sign.bind.usage");
            return;
        }
        Block target = core.platform().players().targetBlock(player, 6);
        if (target == null || !(target.getState() instanceof Sign)) {
            core.messageService().send(player, "lobby.admin.sign.not-looking-at-sign");
            return;
        }
        GameId gameId = GameId.of(args[1].toLowerCase(Locale.ROOT));
        // Fleet-wide view: on a multi-server deployment the game plugin lives on the arena servers.
        if (!core.coordination().allHostedGames().contains(gameId)) {
            core.messageService().send(player, "lobby.play.unknown-game",
                    Map.of("game", gameId.value()));
            return;
        }
        String mapFilter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;

        ConnectionSign sign = new ConnectionSign(
                target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ(),
                gameId, mapFilter);
        signs.bind(sign);
        core.messageService().send(player, "lobby.admin.sign.bind.done", Map.of(
                "game", gameId.value(),
                "map", mapFilter == null ? "any" : mapFilter,
                "x", String.valueOf(sign.x()),
                "y", String.valueOf(sign.y()),
                "z", String.valueOf(sign.z())));
    }

    private void handleSignUnbind(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        Block target = core.platform().players().targetBlock(player, 6);
        if (target == null || !(target.getState() instanceof Sign signState)) {
            core.messageService().send(player, "lobby.admin.sign.not-looking-at-sign");
            return;
        }
        boolean removed = signs.unbind(target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ());
        if (removed) {
            for (int i = 0; i < 4; i++)
                signState.setLine(i, "");
            signState.update();
            core.messageService().send(player, "lobby.admin.sign.unbind.done");
        } else {
            core.messageService().send(player, "lobby.admin.sign.unbind.not-bound");
        }
    }

    private void handleSignList(CommandSender sender) {
        core.messageService().sendToSender(sender, "lobby.admin.sign.list.header");
        int shown = 0;
        for (ConnectionSign sign : signs.all()) {
            core.messageService().sendToSender(sender, "lobby.admin.sign.list.entry", Map.of(
                    "world", sign.world(),
                    "x", String.valueOf(sign.x()),
                    "y", String.valueOf(sign.y()),
                    "z", String.valueOf(sign.z()),
                    "game", sign.gameId().value(),
                    "map", sign.acceptsAnyMap() ? "any" : sign.mapFilter()));
            shown++;
        }
        if (shown == 0) {
            core.messageService().sendToSender(sender, "lobby.admin.sign.list.empty");
        }
    }
}
