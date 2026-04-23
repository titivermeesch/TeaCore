package me.playbosswar.tea.core.api.transport;

import org.bukkit.entity.Player;

/**
 * Moves players between backend servers through the proxy.
 *
 * <p>
 * The default implementation writes {@code Connect} frames on the legacy
 * {@code BungeeCord} plugin-message channel, which both BungeeCord and Velocity
 * honor.
 * In standalone mode the implementation is a no-op.
 */
public interface ProxyTransport {

    /**
     * Requests the proxy to move a player to another backend server.
     *
     * <p>
     * On Velocity the target name must match an entry in {@code velocity.toml}'s
     * {@code [servers]} block; on BungeeCord it must match an entry in
     * {@code config.yml}.
     * The call sends a plugin message frame and returns immediately; the actual
     * transfer
     * happens asynchronously on the proxy and cannot be cancelled here. If the
     * player
     * never reaches the destination (e.g. the server is down), the proxy surfaces
     * the
     * error to the client.
     *
     * @param player           player to move; must not be {@code null}.
     * @param targetServerName destination server name as configured on the proxy;
     *                         must
     *                         not be {@code null}.
     */
    void connect(Player player, String targetServerName);
}
