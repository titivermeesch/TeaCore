package me.playbosswar.tea.core.impl.arena;

import java.time.Duration;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.impl.coordination.Matchmaker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Moves players out of arena worlds on quit and on join, and dequeues players on quit so a reconnect doesn't route them into an arena they
 * no longer want. Skipped inside the matchmaker's "recently routed" window, where
 * a quit is a proxy hop to an arena server rather than a disconnect.
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

    /** Before the lobby's spawn handling, so it sees the player outside the arena world. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A routed player spawns in the waiting room and is added a tick later.
        if (arenaManager.isStranded(player) && coordination.queueEntryOf(player.getUniqueId()).isEmpty()) {
            arenaManager.rescueStranded(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        arenaManager.handleDisconnect(event.getPlayer());
        if (matchmaker.wasRecentlyRouted(event.getPlayer().getUniqueId(), ROUTING_GRACE)) {
            // Mid proxy hop: the destination server dequeues them on arrival.
            return;
        }
        coordination.dequeue(event.getPlayer().getUniqueId());
    }
}
