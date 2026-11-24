package me.playbosswar.tea.core.api.platform;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Entity settings that newer versions expose directly and older ones only via NBT. */
public interface EntityControl {

    /** No AI: the mob stands still and doesn't turn or attack. */
    void setAi(LivingEntity entity, boolean ai);

    void setInvulnerable(Entity entity, boolean invulnerable);

    /** No sounds. Not available before 1.9 (no-op). */
    void setSilent(Entity entity, boolean silent);

    /** No gravity. Not available before 1.10 (no-op). */
    void setGravity(Entity entity, boolean gravity);

    /** A glowing outline seen through walls. Not available before 1.9 (no-op). */
    void setGlowing(Entity entity, boolean glowing);

    /** Players walk through it. Not available before 1.9 (no-op). */
    void setCollidable(LivingEntity entity, boolean collidable);

    double maxHealth(LivingEntity entity);

    /**
     * An attribute's current value, by its modern name: MAX_HEALTH,
     * MOVEMENT_SPEED, KNOCKBACK_RESISTANCE, ATTACK_DAMAGE, FOLLOW_RANGE (all
     * versions) or a newer one such as SCALE. NaN if this version lacks it.
     */
    double attribute(LivingEntity entity, String modernName);

    /** Sets an attribute's base value (names as in {@link #attribute}); no-op if this version lacks it. */
    void setAttributeBase(LivingEntity entity, String modernName, double value);

    /**
     * The player responsible for damage from {@code damager}: the player
     * itself, the shooter of a projectile, whoever lit the TNT, or (1.9+) the
     * source of a lingering potion cloud. Null if no player.
     */
    @org.jetbrains.annotations.Nullable org.bukkit.entity.Player playerBehind(@org.jetbrains.annotations.Nullable Entity damager);
}
