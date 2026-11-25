package me.playbosswar.tea.core.impl.platform.modern;

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

/** All WorldEdit code lives here; this class is only loaded when FAWE/WE is enabled. */
final class WorldEditPaster {

    private WorldEditPaster() {
    }

    public static void paste(Path schematicFile, World world, int x, int y, int z)
            throws IOException, WorldEditException {
        Clipboard clipboard = readClipboard(schematicFile);
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        try (EditSession session = editSession(weWorld)) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(op);
        }
    }

    /**
     * Takes and returns plain int triples so callers never reference a WorldEdit type.
     *
     * @return the region's min corner as {@code [x, y, z]}.
     */
    public static int[] saveRegion(World world, int ax, int ay, int az, int bx, int by, int bz,
            Path outputFile) throws IOException, WorldEditException {
        BlockVector3 cornerA = BlockVector3.at(ax, ay, az);
        BlockVector3 cornerB = BlockVector3.at(bx, by, bz);
        BlockVector3 min = BlockVector3.at(
                Math.min(x(cornerA), x(cornerB)),
                Math.min(y(cornerA), y(cornerB)),
                Math.min(z(cornerA), z(cornerB)));
        BlockVector3 max = BlockVector3.at(
                Math.max(x(cornerA), x(cornerB)),
                Math.max(y(cornerA), y(cornerB)),
                Math.max(z(cornerA), z(cornerB)));

        // Editor markers are non-persistent text displays: never part of the map.
        Class<?> textDisplay = OldApis.type("org.bukkit.entity.TextDisplay");
        if (textDisplay != null) {
            for (org.bukkit.entity.Entity marker : world.getEntitiesByClass(textDisplay.asSubclass(org.bukkit.entity.Entity.class))) {
                if (!marker.isPersistent()) {
                    marker.remove();
                }
            }
        }
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        CuboidRegion region = new CuboidRegion(weWorld, min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(min);

        try (EditSession session = editSession(weWorld)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, min);
            Operations.complete(copy);
        }

        Files.createDirectories(outputFile.getParent());
        try (OutputStream os = Files.newOutputStream(outputFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                ClipboardWriter writer = spongeFormat().getWriter(os)) {
            writer.write(clipboard);
        }
        return new int[]{x(min), y(min), z(min)};
    }

    // BlockVector3 became a record in WorldEdit 7.3; older versions only have getX().
    @SuppressWarnings("removal")
    private static int x(BlockVector3 v) {
        try {
            return v.x();
        } catch (NoSuchMethodError oldWorldEdit) {
            return v.getX();
        }
    }

    @SuppressWarnings("removal")
    private static int y(BlockVector3 v) {
        try {
            return v.y();
        } catch (NoSuchMethodError oldWorldEdit) {
            return v.getY();
        }
    }

    @SuppressWarnings("removal")
    private static int z(BlockVector3 v) {
        try {
            return v.z();
        } catch (NoSuchMethodError oldWorldEdit) {
            return v.getZ();
        }
    }

    /** WorldEdit before 7.2 (still found on 1.16.5 servers) has no session builder. */
    @SuppressWarnings("deprecation")
    private static EditSession editSession(com.sk89q.worldedit.world.World weWorld) {
        try {
            return WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build();
        } catch (NoSuchMethodError oldWorldEdit) {
            return WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1);
        }
    }

    /** Sponge v3 needs WorldEdit 7.3; older versions write v2. */
    private static ClipboardFormat spongeFormat() {
        for (String name : new String[] {"SPONGE_V3_SCHEMATIC", "SPONGE_SCHEMATIC"}) {
            try {
                return BuiltInClipboardFormat.valueOf(name);
            } catch (IllegalArgumentException notInThisVersion) {
                // try the next one
            }
        }
        throw new IllegalStateException("WorldEdit has no Sponge schematic format");
    }

    /** Returns {@code [width, height, length]}. */
    public static int[] readDimensions(Path schematicFile) throws IOException {
        BlockVector3 dims = readClipboard(schematicFile).getDimensions();
        return new int[]{x(dims), y(dims), z(dims)};
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
