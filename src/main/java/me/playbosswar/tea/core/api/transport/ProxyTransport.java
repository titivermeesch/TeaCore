package me.playbosswar.tea.core.api.transport;

import org.bukkit.entity.Player;

/**
 * Moves players between servers through the proxy, using the
 * {@code BungeeCord} plugin-message channel (BungeeCord and Velocity). No-op
 * in standalone mode.
 */
public interface ProxyTransport {

    /**
     * Returns immediately; the proxy performs the transfer and reports any
     * failure to the player.
     *
     * @param targetServerName server name as configured on the proxy
     */
    void connect(Player player, String targetServerName);
}
