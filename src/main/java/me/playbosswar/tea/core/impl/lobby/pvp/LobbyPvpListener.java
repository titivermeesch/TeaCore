package me.playbosswar.tea.core.impl.lobby.pvp;

import me.playbosswar.tea.core.api.TeaCoreAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;

/** Read on every hit, so {@code /tea reload} applies a {@code lobby.pvp} change at once. */
public final class LobbyPvpListener implements Listener {

    private final Plugin plugin;
    private final TeaCoreAPI core;

    public LobbyPvpListener(Plugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("lobby.enabled", false)
                || plugin.getConfig().getBoolean("lobby.pvp", false)) {
            return;
        }
        Player attacker = playerBehind(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (inArena(victim) || inArena(attacker)) {
            return; // a game decides what happens in its arenas
        }
        event.setCancelled(true);
    }

    private boolean inArena(Player player) {
        return core.arenas().arenaOf(player.getUniqueId()).isPresent();
    }

    private static Player playerBehind(Entity damager) {
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().entities().playerBehind(damager);
    }
}
