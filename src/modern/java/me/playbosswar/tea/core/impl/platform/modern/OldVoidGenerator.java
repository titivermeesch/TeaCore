package me.playbosswar.tea.core.impl.platform.modern;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

/** Void worlds for 1.16.5 - 1.17, which only call generateChunkData (the shouldGenerate* hooks are 1.18+). */
@SuppressWarnings("deprecation")
final class OldVoidGenerator extends ChunkGenerator {

    @Override
    public @NotNull ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z,
            @NotNull BiomeGrid biome) {
        return createChunkData(world);
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0, 64, 0);
    }
}
