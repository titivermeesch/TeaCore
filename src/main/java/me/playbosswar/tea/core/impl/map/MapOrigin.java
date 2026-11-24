package me.playbosswar.tea.core.impl.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Schematics are saved with their min corner at this point and spawns are stored as
 * deltas from it. Changing these constants moves every existing map.
 */
public final class MapOrigin {

    public static final int X = 0;
    public static final int Y = 64;
    public static final int Z = 0;

    private MapOrigin() {
    }

    public static Location in(World world) {
        return new Location(world, X, Y, Z);
    }
}
