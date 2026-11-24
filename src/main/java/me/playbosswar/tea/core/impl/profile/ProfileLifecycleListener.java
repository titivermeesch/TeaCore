package me.playbosswar.tea.core.impl.profile;

import java.time.Instant;
import me.playbosswar.tea.core.api.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Database work stays off the server thread: the profile loads in async pre-login and saves run async. */
public final class ProfileLifecycleListener implements Listener {

    private final PlayerProfileServiceImpl service;
    private final org.bukkit.plugin.Plugin plugin;

    public ProfileLifecycleListener(PlayerProfileServiceImpl service, org.bukkit.plugin.Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            service.getOrCreate(event.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Already cached by onPreLogin; a cache miss (e.g. after /reload) still loads here.
        PlayerProfile profile = service.getOrCreate(player.getUniqueId());
        if (profile instanceof PlayerProfileImpl impl) {
            impl.setLastSeen(Instant.now());
            impl.setLocale(me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().locale(player));
            persistLater(impl);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.get(event.getPlayer().getUniqueId()).ifPresent(p -> {
            if (p instanceof PlayerProfileImpl impl) {
                impl.setLastSeen(Instant.now());
                persistLater(impl);
            }
        });
    }

    private void persistLater(PlayerProfileImpl profile) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> service.persist(profile));
        } else {
            service.persist(profile);
        }
    }
}
