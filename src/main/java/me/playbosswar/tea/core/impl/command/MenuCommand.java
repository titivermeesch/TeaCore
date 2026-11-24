package me.playbosswar.tea.core.impl.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.menu.MenuServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Opening a menu for someone else needs {@code tea.menu.others} and skips the menu's
 * own {@code permission:} (for NPC and sign plugins that run commands).
 */
public final class MenuCommand implements TabExecutor {

    private final TeaCorePlugin plugin;
    private final MenuServiceImpl menus;

    public MenuCommand(TeaCorePlugin plugin, MenuServiceImpl menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageService ms = plugin.messageService();
        if (args.length < 1) {
            ms.sendToSender(sender, "core.menu.usage");
            return true;
        }
        String id = args[0];
        Map<String, String> values = new java.util.HashMap<>();
        String targetName = null;
        for (int i = 1; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) {
                values.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            } else {
                targetName = args[i];
            }
        }
        Player target;
        if (targetName != null) {
            if (!sender.hasPermission("tea.menu.others")) {
                ms.sendToSender(sender, "core.command.no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                ms.sendToSender(sender, "core.menu.player-offline", Map.of("player", targetName));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            ms.sendToSender(sender, "core.command.player-only");
            return true;
        }
        if (!menus.isEnabled(id)) {
            ms.sendToSender(sender, "core.menu.unknown", Map.of("id", id));
            return true;
        }
        if (sender.hasPermission("tea.menu.others")) {
            menus.openBypassingPermission(target, id, values);
        } else if (!menus.open(target, id, values)) {
            ms.sendToSender(sender, "core.menu.no-permission");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return menus.menuIds().stream().filter(menus::isEnabled).filter(id -> id.startsWith(prefix)).sorted().toList();
        }
        if (args.length == 2 && sender.hasPermission("tea.menu.others")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
