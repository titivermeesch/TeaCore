package me.playbosswar.tea.core.impl.arena;

import java.time.Duration;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.impl.coordination.Matchmaker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * On quit: leave the player's current arena (if any), and remove any pending
 * matchmaker queue entry for them. Dequeueing prevents stale entries from
 * sitting in Redis across reconnects — without this, a player who runs
 * {@code /play} then disconnects before the matchmaker routes them gets
 * teleported into an arena the moment they reconnect, even if they didn't
 * intend to keep their queue spot.
 *
 * <p>
 * One important subtlety: a {@link PlayerQuitEvent} on the lobby looks
 * identical for "player closed Minecraft" and "player is being proxy-hopped
 * to an arena server". To avoid losing the queue entry on legitimate cross-
 * server routing, we check the {@link Matchmaker}'s "recently routed"
 * tracker and skip the dequeue inside that grace window. After the window
 * (15s) expires, future quit events dequeue normally.
 */
public final class ArenaDisconnectListener implements Listener {

    /** Grace window during which a quit is treated as a proxy hop, not a real disconnect. */
    private static final Duration ROUTING_GRACE = Duration.ofSeconds(15);

    private final ArenaManagerImpl arenaManager;
    private final CoordinationBackend coordination;
    private final Matchmaker matchmaker;

    public ArenaDisconnectListener(ArenaManagerImpl arenaManager, CoordinationBackend coordination,
            Matchmaker matchmaker) {
        this.arenaManager = arenaManager;
        this.coordination = coordination;
        this.matchmaker = matchmaker;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        arenaManager.handleDisconnect(event.getPlayer().getUniqueId());
        if (matchmaker.wasRecentlyRouted(event.getPlayer().getUniqueId(), ROUTING_GRACE)) {
            // Player is mid-proxy-hop; the destination server's matchmaker
            // will dequeue them when they arrive. Don't strip the queue
            // entry here.
            return;
        }
        coordination.dequeue(event.getPlayer().getUniqueId());
    }
}
