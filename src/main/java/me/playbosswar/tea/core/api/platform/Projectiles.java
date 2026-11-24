package me.playbosswar.tea.core.api.platform;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What a projectile hit. 1.11+ servers say so in the event; on older ones
 * TeaCore works it out from where the projectile was and where it was going.
 */
public interface Projectiles {

    @Nullable Entity hitEntity(ProjectileHitEvent event);

    @Nullable Block hitBlock(ProjectileHitEvent event);

    /** The face of {@link #hitBlock} that was hit, or {@code null}. */
    @Nullable BlockFace hitFace(ProjectileHitEvent event);
}
