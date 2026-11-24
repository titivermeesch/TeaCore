package me.playbosswar.tea.core.impl.map;

import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.World;

/**
 * Modern servers use WorldEdit/FAWE with Sponge {@code .schem} files (and can read old
 * {@code .schematic} files). Legacy servers (1.8 to 1.12) use TeaCore's own MCEdit
 * {@code .schematic} support and need no WorldEdit.
 */
public interface SchematicStore {

    /** Without the dot. */
    String extension();

    /** Whether pasting/saving works right now (WorldEdit installed on modern servers). */
    boolean available();

    String unavailableReason();

    void paste(Path file, World world, int x, int y, int z) throws IOException;

    /** Saves the region; returns its min corner as {@code [x, y, z]}. */
    int[] saveRegion(World world, int ax, int ay, int az, int bx, int by, int bz, Path out) throws IOException;

    /** {@code [width, height, length]}. */
    int[] readDimensions(Path file) throws IOException;

    static SchematicStore current() {
        return ((me.playbosswar.tea.core.impl.platform.PlatformInternals)
                me.playbosswar.tea.core.api.TeaCoreAPI.get().platform()).schematics();
    }
}
