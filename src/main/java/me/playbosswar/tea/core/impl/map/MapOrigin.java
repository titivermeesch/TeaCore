package me.playbosswar.tea.core.impl.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Canonical paste origin for maps in arena worlds. Schematics are saved with
 * their
 * min-corner mapped to this point, and spawn coordinates in
 * {@code MapDefinition}
 * are stored as deltas from this origin. Keep all arena/map code in sync with
 * these
 * constants - if they change, existing maps will teleport to the wrong spot.
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
