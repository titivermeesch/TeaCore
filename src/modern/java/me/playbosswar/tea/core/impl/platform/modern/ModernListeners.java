package me.playbosswar.tea.core.impl.platform.modern;

import me.playbosswar.tea.core.impl.platform.Ghosts;
import me.playbosswar.tea.core.impl.platform.LockedItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Events that only exist on 1.9+ servers. */
final class ModernListeners implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (Ghosts.isGhost(event.getPlayer())
                || LockedItems.isLocked(event.getMainHandItem()) || LockedItems.isLocked(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /** Projectiles fly through ghosts (cancellable since 1.20). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getHitEntity() != null && Ghosts.isGhost(event.getHitEntity())
                && (Object) event instanceof org.bukkit.event.Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
    }
}
