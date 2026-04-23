package me.playbosswar.tea.core.impl.arena;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ArenaDisconnectListener implements Listener {

    private final ArenaManagerImpl arenaManager;

    public ArenaDisconnectListener(ArenaManagerImpl arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        arenaManager.handleDisconnect(event.getPlayer().getUniqueId());
    }
}
