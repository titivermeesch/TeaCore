package me.playbosswar.tea.core.impl.transport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;

/** Uses the legacy {@code BungeeCord} channel name. */
public final class BungeeChannelTransport implements ProxyTransport {

    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;

    public BungeeChannelTransport(Plugin plugin) {
        this.plugin = plugin;
        Messenger messenger = plugin.getServer().getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
    }

    @Override
    public void connect(Player player, String targetServerName) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(targetServerName);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send proxy-connect to " + targetServerName
                    + " for " + player.getName() + ": " + e.getMessage());
        }
    }
}
