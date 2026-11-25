package me.playbosswar.tea.core.impl.platform.legacy;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

/**
 * Empty chunks with a fixed spawn. Without the fixed spawn, a new world searches
 * up to 1000 random columns for a spawn block, loading hundreds of chunks.
 */
final class LegacyVoidGenerator extends ChunkGenerator {

    @Override
    @SuppressWarnings("deprecation")
    public byte[][] generateBlockSections(World world, Random random, int x, int z, BiomeGrid biomes) {
        return new byte[world.getMaxHeight() / 16][];
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0, 64, 0);
    }
}
