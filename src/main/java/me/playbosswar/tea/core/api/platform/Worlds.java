package me.playbosswar.tea.core.api.platform;

import org.bukkit.World;
import org.bukkit.WorldCreator;

/** World differences between versions. */
public interface Worlds {

    /** Lowest buildable Y (0 before 1.18, -64 on overworlds since). */
    int minHeight(World world);

    int maxHeight(World world);

    /** Sets a game rule by its name (e.g. "doMobSpawning"); unknown rules are ignored. */
    void setGameRule(World world, String rule, String value);

    /** Whether the chunk was ever generated (loads it on versions before 1.13). */
    boolean isChunkGenerated(World world, int chunkX, int chunkZ);

    /** Whether the block in the snapshot is air (chunk-relative x/z). */
    boolean isAir(org.bukkit.ChunkSnapshot snapshot, int x, int y, int z);

    /**
     * An explosion caused by {@code source} (so damage is credited to it and
     * friendly-fire / protection checks see who did it), or by nothing.
     */
    void explode(org.bukkit.Location at, float power, boolean setFire, boolean breakBlocks,
                 @org.jetbrains.annotations.Nullable org.bukkit.entity.Entity source);

    /** A creator for an empty (void) world. */
    WorldCreator voidWorld(String name);
}
