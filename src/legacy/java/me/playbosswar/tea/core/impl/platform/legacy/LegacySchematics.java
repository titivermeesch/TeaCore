package me.playbosswar.tea.core.impl.platform.legacy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import me.playbosswar.tea.core.impl.map.SchematicStore;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * MCEdit {@code .schematic} files (block ids + data values, gzipped NBT), the
 * format of 1.8 - 1.12. No WorldEdit needed. Blocks only: tile entity contents
 * and entities aren't stored (games fill chests themselves).
 */
@SuppressWarnings("deprecation")
final class LegacySchematics implements SchematicStore {

    @Override public String extension() { return "schematic"; }
    @Override public boolean available() { return true; }
    @Override public String unavailableReason() { return ""; }

    @Override
    public void paste(Path file, World world, int ox, int oy, int oz) throws IOException {
        Map<String, Object> root = read(file);
        int w = num(root, "Width"), h = num(root, "Height"), l = num(root, "Length");
        byte[] blocks = (byte[]) root.get("Blocks");
        byte[] data = (byte[]) root.get("Data");
        byte[] add = (byte[]) root.get("AddBlocks");
        if (blocks == null || data == null) {
            throw new IOException(file.getFileName() + " is not an MCEdit schematic (saved on a newer version?)");
        }
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int i = (y * l + z) * w + x;
                    int id = blocks[i] & 0xFF;
                    if (add != null) {
                        int nibble = (i >> 1) < add.length ? add[i >> 1] : 0;
                        id |= ((i & 1) == 0 ? nibble & 0x0F : (nibble >> 4) & 0x0F) << 8;
                    }
                    if (id == 0) {
                        continue; // arenas start empty: skip air
                    }
                    Block b = world.getBlockAt(ox + x, oy + y, oz + z);
                    b.setTypeIdAndData(id, (byte) (data[i] & 0x0F), false);
                }
            }
        }
    }

    @Override
    public int[] saveRegion(World world, int ax, int ay, int az, int bx, int by, int bz, Path out) throws IOException {
        int minX = Math.min(ax, bx), minY = Math.min(ay, by), minZ = Math.min(az, bz);
        int w = Math.abs(ax - bx) + 1, h = Math.abs(ay - by) + 1, l = Math.abs(az - bz) + 1;
        byte[] blocks = new byte[w * h * l];
        byte[] data = new byte[w * h * l];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    Block b = world.getBlockAt(minX + x, minY + y, minZ + z);
                    int i = (y * l + z) * w + x;
                    blocks[i] = (byte) b.getTypeId();
                    data[i] = b.getData();
                }
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Width", (short) w);
        root.put("Height", (short) h);
        root.put("Length", (short) l);
        root.put("Materials", "Alpha");
        root.put("Blocks", blocks);
        root.put("Data", data);
        root.put("Entities", new ArrayList<>());
        root.put("TileEntities", new ArrayList<>());
        Files.createDirectories(out.getParent());
        try (DataOutputStream os = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(out)))) {
            os.writeByte(10);
            os.writeUTF("Schematic");
            writeCompound(os, root);
        }
        return new int[] {minX, minY, minZ};
    }

    @Override
    public int[] readDimensions(Path file) throws IOException {
        Map<String, Object> root = read(file);
        return new int[] {num(root, "Width"), num(root, "Height"), num(root, "Length")};
    }

    private static int num(Map<String, Object> root, String key) throws IOException {
        Object v = root.get(key);
        if (!(v instanceof Number n)) {
            throw new IOException("schematic has no " + key);
        }
        return n.intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            if (in.readByte() != 10) {
                throw new IOException(file.getFileName() + " is not NBT");
            }
            in.readUTF();
            return (Map<String, Object>) readPayload(in, 10);
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case 8: return in.readUTF();
            case 9: {
                int inner = in.readByte(); int n = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) list.add(readPayload(in, inner));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) return map;
                    String name = in.readUTF();
                    map.put(name, readPayload(in, t));
                }
            }
            case 11: { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case 12: { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("bad NBT tag " + type);
        }
    }

    private static void writeCompound(DataOutputStream os, Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            int type = v instanceof Short ? 2 : v instanceof Integer ? 3 : v instanceof byte[] ? 7
                    : v instanceof String ? 8 : v instanceof List ? 9 : 10;
            os.writeByte(type);
            os.writeUTF(e.getKey());
            switch (type) {
                case 2 -> os.writeShort((Short) v);
                case 3 -> os.writeInt((Integer) v);
                case 7 -> { byte[] b = (byte[]) v; os.writeInt(b.length); os.write(b); }
                case 8 -> os.writeUTF((String) v);
                case 9 -> { os.writeByte(10); os.writeInt(0); } // empty compound lists
                default -> { }
            }
        }
        os.writeByte(0);
    }
}
