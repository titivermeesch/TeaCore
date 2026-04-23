package me.playbosswar.tea.core.impl.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.playbosswar.tea.core.api.chat.ChatChannel;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TeaChatCommand implements CommandExecutor, TabCompleter {

    private final TeaCorePlugin plugin;

    public TeaChatCommand(TeaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.messageService().sendToSender(player, "core.chat.usage");
            return true;
        }
        try {
            ChatChannel channel = ChatChannel.valueOf(args[0].toUpperCase(Locale.ROOT));
            if (channel == ChatChannel.STAFF && !player.hasPermission("tea.chat.staff")) {
                plugin.messageService().send(player, "core.command.no-permission");
                return true;
            }
            if (channel == ChatChannel.GLOBAL && !player.hasPermission("tea.chat.global")) {
                plugin.messageService().send(player, "core.command.no-permission");
                return true;
            }
            plugin.chatRouter().setActiveChannel(player, channel);
            plugin.messageService().send(player, "core.chat.active-channel",
                    Map.of("channel", channel.name().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            plugin.messageService().send(player, "core.chat.unknown-channel",
                    Map.of("channel", args[0]));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(ChatChannel.values())
                    .map(c -> c.name().toLowerCase(Locale.ROOT))
                    .filter(s -> s.startsWith(p))
                    .toList();
        }
        return List.of();
    }
}
