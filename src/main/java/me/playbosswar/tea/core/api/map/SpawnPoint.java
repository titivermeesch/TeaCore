package me.playbosswar.tea.core.api.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * A spawn position stored as an offset from the map's paste origin.
 *
 * <p>
 * Use {@link #toLocation(Location)} at runtime to compute the absolute world
 * location
 * by adding this offset to the arena's paste origin.
 *
 * @param id    stable identifier for this spawn within the map; never
 *              {@code null}.
 * @param x     offset on the X axis in blocks.
 * @param y     offset on the Y axis in blocks.
 * @param z     offset on the Z axis in blocks.
 * @param yaw   facing yaw in degrees.
 * @param pitch facing pitch in degrees.
 */
public record SpawnPoint(String id, double x, double y, double z, float yaw, float pitch) {

    /**
     * Returns an absolute {@link Location} by adding this offset to a paste origin.
     *
     * @param pasteOrigin origin in some world; must not be {@code null} and must
     *                    have a
     *                    non-null world.
     * @return the absolute location, never {@code null}.
     */
    public Location toLocation(Location pasteOrigin) {
        return new Location(
                pasteOrigin.getWorld(),
                pasteOrigin.getX() + x,
                pasteOrigin.getY() + y,
                pasteOrigin.getZ() + z,
                yaw,
                pitch);
    }

    /**
     * Interprets this spawn's coordinates as absolute in the given world.
     *
     * <p>
     * Useful during map authoring where offsets are being captured relative to the
     * world
     * origin rather than a paste origin.
     *
     * @param world world the location should live in; must not be {@code null}.
     * @return the absolute location, never {@code null}.
     */
    public Location toAbsolute(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Factory for a spawn with zero rotation.
     *
     * @param id spawn id; must not be {@code null}.
     * @param x  X offset in blocks.
     * @param y  Y offset in blocks.
     * @param z  Z offset in blocks.
     * @return a new {@link SpawnPoint} with yaw and pitch of {@code 0}, never
     *         {@code null}.
     */
    public static SpawnPoint of(String id, double x, double y, double z) {
        return new SpawnPoint(id, x, y, z, 0f, 0f);
    }
}
