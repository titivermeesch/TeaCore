package me.playbosswar.tea.core.impl.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.stats.PlayerStatsGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class PlayerStatsCommand implements TabExecutor {

    private final TeaCorePlugin plugin;
    private final PlayerStatsGui gui;

    public PlayerStatsCommand(TeaCorePlugin plugin, PlayerStatsGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return true;
        }
        if (!sender.hasPermission("tea.stats")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return true;
        }

        UUID targetUuid;
        String targetName;
        if (args.length >= 1) {
            if (!sender.hasPermission("tea.admin")) {
                plugin.messageService().sendToSender(sender, "core.command.no-permission");
                return true;
            }
            org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[0]);
            targetUuid = target.getUniqueId();
            targetName = target.getName() == null ? args[0] : target.getName();
        } else {
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        var stats = plugin.coreStats().statsOf(targetUuid);
        Map<String, String> menuArgs = new java.util.HashMap<>();
        menuArgs.put("target", targetName);
        menuArgs.put("target_uuid", targetUuid.toString());
        menuArgs.put("wins", String.valueOf(stats.wins()));
        menuArgs.put("losses", String.valueOf(stats.losses()));
        menuArgs.put("games_played", String.valueOf(stats.gamesPlayed()));
        menuArgs.put("kills", String.valueOf(stats.kills()));
        menuArgs.put("deaths", String.valueOf(stats.deaths()));
        menuArgs.put("assists", String.valueOf(stats.assists()));
        menuArgs.put("winrate", stats.gamesPlayed() == 0 ? "0.0" : String.format(java.util.Locale.ROOT, "%.1f",
                100.0 * stats.wins() / stats.gamesPlayed()));
        menuArgs.put("kdr", String.format(java.util.Locale.ROOT, "%.2f",
                (double) stats.kills() / Math.max(1, stats.deaths())));
        if (!plugin.menus().open(player, "stats", menuArgs)) {
            gui.open(player, targetUuid, targetName);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("tea.admin")) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
