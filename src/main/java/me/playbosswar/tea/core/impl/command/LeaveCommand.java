package me.playbosswar.tea.core.impl.command;

import java.util.List;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /leave} — drops the player from their current arena, or removes
 * them from the matchmaker queue if they're not in one yet.
 *
 * <p>
 * Lives in TeaCore (not TeaLobby) because players need to be able to leave
 * an arena from the arena server, where TeaLobby is typically not installed.
 * Behavior order:
 * <ol>
 * <li>If the player is in an arena, remove them with reason
 * {@link ArenaPlayerLeaveEvent.LeaveReason#COMMAND}.</li>
 * <li>Otherwise, if they're in any matchmaker queue, dequeue them.</li>
 * <li>Otherwise, tell them they're not in anything.</li>
 * </ol>
 */
public final class LeaveCommand implements TabExecutor {

    private final TeaCorePlugin plugin;

    public LeaveCommand(TeaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return true;
        }
        if (!sender.hasPermission("tea.arena.play")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return true;
        }
        boolean removed = plugin.arenas().removePlayer(player, ArenaPlayerLeaveEvent.LeaveReason.COMMAND);
        if (removed) {
            plugin.messageService().send(player, "core.leave.success");
            return true;
        }
        boolean dequeued = plugin.coordination().queueEntryOf(player.getUniqueId()).isPresent();
        if (dequeued) {
            plugin.coordination().dequeue(player.getUniqueId());
            plugin.messageService().send(player, "core.leave.dequeued");
            return true;
        }
        plugin.messageService().send(player, "core.leave.nothing-to-leave");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
