package me.playbosswar.tea.core.impl.map;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.bukkit.World;

/**
 * All WorldEdit-dependent code lives here; loading this class pulls WE onto the
 * classpath,
 * so we only touch it when FAWE/WE is actually enabled on the server.
 */
public final class WorldEditPaster {

    private WorldEditPaster() {
    }

    public static void paste(Path schematicFile, World world, int x, int y, int z)
            throws IOException, WorldEditException {
        Clipboard clipboard = readClipboard(schematicFile);
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(op);
        }
    }

    public static BlockVector3 saveRegion(World world, BlockVector3 cornerA, BlockVector3 cornerB,
            Path outputFile) throws IOException, WorldEditException {
        BlockVector3 min = BlockVector3.at(
                Math.min(cornerA.x(), cornerB.x()),
                Math.min(cornerA.y(), cornerB.y()),
                Math.min(cornerA.z(), cornerB.z()));
        BlockVector3 max = BlockVector3.at(
                Math.max(cornerA.x(), cornerB.x()),
                Math.max(cornerA.y(), cornerB.y()),
                Math.max(cornerA.z(), cornerB.z()));

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        CuboidRegion region = new CuboidRegion(weWorld, min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(min);

        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, min);
            Operations.complete(copy);
        }

        Files.createDirectories(outputFile.getParent());
        try (OutputStream os = Files.newOutputStream(outputFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(os)) {
            writer.write(clipboard);
        }
        return min;
    }

    public static BlockVector3 readDimensions(Path schematicFile) throws IOException {
        Clipboard c = readClipboard(schematicFile);
        return c.getDimensions();
    }

    private static Clipboard readClipboard(Path schematicFile) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile.toFile());
        if (format == null) {
            throw new IOException("Unknown schematic format: " + schematicFile);
        }
        try (FileInputStream fis = new FileInputStream(schematicFile.toFile());
                ClipboardReader reader = format.getReader(fis)) {
            return reader.read();
        }
    }
}
