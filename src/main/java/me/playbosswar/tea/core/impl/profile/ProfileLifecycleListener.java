package me.playbosswar.tea.core.impl.profile;

import java.time.Instant;
import me.playbosswar.tea.core.api.profile.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ProfileLifecycleListener implements Listener {

    private final PlayerProfileServiceImpl service;

    public ProfileLifecycleListener(PlayerProfileServiceImpl service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = service.getOrCreate(player.getUniqueId());
        if (profile instanceof PlayerProfileImpl impl) {
            impl.setLastSeen(Instant.now());
            impl.setLocale(player.getLocale());
            service.persist(impl);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.get(event.getPlayer().getUniqueId()).ifPresent(p -> {
            if (p instanceof PlayerProfileImpl impl) {
                impl.setLastSeen(Instant.now());
                service.persist(impl);
            }
        });
    }
}
