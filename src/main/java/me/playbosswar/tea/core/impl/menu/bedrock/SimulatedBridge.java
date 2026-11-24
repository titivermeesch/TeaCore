package me.playbosswar.tea.core.impl.menu.bedrock;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Test stand-in for Floodgate, enabled with {@code -Dtea.bedrock.simulate=Name1,Name2}
 * ({@code Br*} matches names starting with Br, {@code *} everyone). Those Java players
 * get forms as chat lines and answer by typing {@code #<n>} or {@code #close}.
 */
public final class SimulatedBridge implements BedrockBridge, Listener {

    private final Plugin plugin;
    private final Set<String> names;
    private final Map<UUID, Form> open = new ConcurrentHashMap<>();

    public SimulatedBridge(Plugin plugin, String spec) {
        this.plugin = plugin;
        this.names = Set.of(spec.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
    }

    @Override
    public boolean isBedrock(Player player) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        for (String n : names) {
            if (n.endsWith("*") ? name.startsWith(n.substring(0, n.length() - 1)) : name.equals(n)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void send(Player player, Form form) {
        open.put(player.getUniqueId(), form);
        player.sendMessage("[form] " + ChatColor.stripColor(form.title()));
        if (!form.content().isBlank()) {
            for (String line : form.content().split("\n")) {
                player.sendMessage("[form] | " + ChatColor.stripColor(line));
            }
        }
        for (int i = 0; i < form.buttons().size(); i++) {
            player.sendMessage("[form] #" + (i + 1) + " " + ChatColor.stripColor(form.buttons().get(i)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String msg = event.getMessage().trim();
        if (!msg.startsWith("#") || !open.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Form form = open.remove(id);
            if (form == null) {
                return;
            }
            if (msg.equalsIgnoreCase("#close")) {
                form.onClose().run();
                return;
            }
            int pick;
            try {
                pick = Integer.parseInt(msg.substring(1).trim()) - 1;
            } catch (NumberFormatException e) {
                pick = -1;
            }
            if (pick < 0 || pick >= form.buttons().size()) {
                form.onClose().run();
            } else {
                form.onPick().accept(pick);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
