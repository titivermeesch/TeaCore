package me.playbosswar.tea.core.impl.lobby.sign;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Right-click a bound sign to join the queue for its game. Breaking a bound
 * sign auto-unbinds it.
 */
public final class ConnectionSignListener implements Listener {

    private final TeaCoreAPI core;
    private final ConnectionSignService signs;

    public ConnectionSignListener(TeaCoreAPI core, ConnectionSignService signs) {
        this.core = core;
        this.signs = signs;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign))
            return;

        ConnectionSign sign = signs.get(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).orElse(null);
        if (sign == null)
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        List<String> mapIds = sign.acceptsAnyMap() ? List.of() : List.of(sign.mapFilter());
        QueueEntry entry = new QueueEntry(
                player.getUniqueId(),
                player.getName(),
                sign.gameId(),
                mapIds,
                Instant.now());
        core.coordination().enqueue(entry);
        core.messageService().send(player, "lobby.play.queued", Map.of(
                "game", sign.gameId().value(),
                "map", sign.acceptsAnyMap() ? "any" : sign.mapFilter()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Sign))
            return;
        boolean removed = signs.unbind(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (removed) {
            core.messageService().send(event.getPlayer(), "lobby.admin.sign.unbind.auto");
        }
    }
}
