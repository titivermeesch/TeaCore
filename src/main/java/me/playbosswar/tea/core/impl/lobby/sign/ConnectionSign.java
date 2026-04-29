package me.playbosswar.tea.core.impl.lobby.sign;

import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * A bound connection sign in the world. {@code mapFilter} is nullable - when
 * present,
 * only arenas running that specific map are considered when aggregating counts
 * and
 * when routing clicks.
 */
public record ConnectionSign(String world, int x, int y, int z, GameId gameId, @Nullable String mapFilter) {

    public boolean acceptsAnyMap() {
        return mapFilter == null || mapFilter.isBlank();
    }

    public Location toLocation(World bukkitWorld) {
        return new Location(bukkitWorld, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + "," + y + "," + z;
    }

    public String key() {
        return key(world, x, y, z);
    }
}
