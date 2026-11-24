package me.playbosswar.tea.core.impl.platform.modern;

import java.io.IOException;
import java.nio.file.Path;
import me.playbosswar.tea.core.impl.map.SchematicStore;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Schematics through WorldEdit/FAWE. WorldEditPaster is only loaded when used. */
final class ModernSchematics implements SchematicStore {

    @Override public String extension() { return "schem"; }

    @Override
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    @Override
    public String unavailableReason() {
        return "WorldEdit/FAWE not enabled. Install WorldEdit or FastAsyncWorldEdit to paste and save maps.";
    }

    @Override
    public void paste(Path file, World world, int x, int y, int z) throws IOException {
        try {
            WorldEditPaster.paste(file, world, x, y, z);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Paste failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int[] saveRegion(World world, int ax, int ay, int az, int bx, int by, int bz, Path out) throws IOException {
        try {
            return WorldEditPaster.saveRegion(world, ax, ay, az, bx, by, bz, out);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Save failed: " + e.getMessage(), e);
        }
    }

    @Override public int[] readDimensions(Path file) throws IOException { return WorldEditPaster.readDimensions(file); }
}
