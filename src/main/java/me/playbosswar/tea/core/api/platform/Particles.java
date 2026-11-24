package me.playbosswar.tea.core.api.platform;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Particles by their modern names (FLAME, SMOKE, CLOUD, EXPLOSION, HEART,
 * HAPPY_VILLAGER, ...), mapped to the closest one on older versions.
 */
public interface Particles {

    /** Spawns for everyone nearby, or only for {@code viewer} when given. */
    void spawn(String particle, Location at, int count, double spreadX, double spreadY, double spreadZ,
               double speed, @Nullable Player viewer);

    default void spawn(String particle, Location at, int count, double spread, double speed) {
        spawn(particle, at, count, spread, spread, spread, speed, null);
    }

    /**
     * Coloured dust. {@code size} is ignored before 1.13 (dust has one size
     * there); {@code count} particles are spread over the box.
     */
    void dust(Location at, Color color, float size, int count, double spread, @Nullable Player viewer);

    default void dust(Location at, Color color, float size, int count, double spread) {
        dust(at, color, size, count, spread, null);
    }
}
