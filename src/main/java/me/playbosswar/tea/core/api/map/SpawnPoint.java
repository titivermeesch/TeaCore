package me.playbosswar.tea.core.api.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * A spawn position stored as an offset in blocks from the map's paste origin.
 * Yaw and pitch are in degrees.
 */
public record SpawnPoint(String id, double x, double y, double z, float yaw, float pitch) {

    /** Adds this offset to {@code pasteOrigin}, keeping its world. */
    public Location toLocation(Location pasteOrigin) {
        return new Location(
                pasteOrigin.getWorld(),
                pasteOrigin.getX() + x,
                pasteOrigin.getY() + y,
                pasteOrigin.getZ() + z,
                yaw,
                pitch);
    }

    /** Treats the coordinates as absolute in {@code world}, ignoring any paste origin. */
    public Location toAbsolute(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** A spawn with zero yaw and pitch. */
    public static SpawnPoint of(String id, double x, double y, double z) {
        return new SpawnPoint(id, x, y, z, 0f, 0f);
    }
}
