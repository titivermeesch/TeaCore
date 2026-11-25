package me.playbosswar.tea.core.impl.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Points file (plugins/TeaCore/imports/&lt;name&gt;.yml): same keys as a map entry in
 * maps/&lt;game&gt;.yml, but with absolute coordinates in the source world. Optional
 * {@code bounds: {min: [x,y,z], max: [x,y,z]}} skips block detection.
 */
public final class MapImporter {

    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int MAX_SPAN = 512;

    private final MapPersistence persistence;
    private final Logger logger;

    public MapImporter(MapPersistence persistence, Logger logger) {
        this.persistence = persistence;
        this.logger = logger;
    }

    /** Min corner in the source world, and size. */
    public record Result(int[] min, int[] size) { }

    public Result importWorld(GameId game, String mapId, String worldName, File pointsFile) throws IOException {
        if (!mapId.matches("[a-z0-9_.-]+") || !worldName.matches("[A-Za-z0-9_.-]+")) {
            throw new IOException("map and world names may only use letters, digits, '_', '.' and '-'");
        }
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (!new File(folder, "level.dat").isFile()) {
            throw new IOException("no world folder " + worldName + " (with level.dat) in the server folder");
        }
        YamlConfiguration points = YamlConfiguration.loadConfiguration(pointsFile);
        World world = Bukkit.getWorld(worldName);
        boolean loadedHere = world == null;
        if (world == null) {
            world = me.playbosswar.tea.core.impl.arena.ArenaWorlds.create(
                    me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().worlds().voidWorld(worldName));
        }
        if (world == null) {
            throw new IOException("could not load world " + worldName);
        }
        try {
            int[] box = points.isList("bounds.min") && points.isList("bounds.max")
                    ? new int[] {points.getIntegerList("bounds.min").get(0), points.getIntegerList("bounds.min").get(1),
                            points.getIntegerList("bounds.min").get(2), points.getIntegerList("bounds.max").get(0),
                            points.getIntegerList("bounds.max").get(1), points.getIntegerList("bounds.max").get(2)}
                    : detect(world, folder);
            if (box == null) {
                throw new IOException("the world has no blocks");
            }
            if (box[3] - box[0] > MAX_SPAN || box[5] - box[2] > MAX_SPAN) {
                throw new IOException("the world spans more than " + MAX_SPAN + " blocks; give bounds in the points file");
            }
            persistence.ensureDirectories();
            Path schem = persistence.schematicsDir().resolve(mapId + "." + SchematicStore.current().extension());
            int[] min;
            try {
                min = SchematicStore.current().saveRegion(world, box[0], box[1], box[2], box[3], box[4], box[5], schem);
            } catch (Exception e) {
                throw new IOException("schematic capture failed: " + e.getMessage(), e);
            }
            writeEntry(game, mapId, points, min);
            return new Result(min, new int[] {box[3] - box[0] + 1, box[4] - box[1] + 1, box[5] - box[2] + 1});
        } finally {
            if (loadedHere) {
                Bukkit.unloadWorld(world, false);
            }
        }
    }

    /** Bounding box {minX,minY,minZ,maxX,maxY,maxZ} of every non-air block in the world's region files. */
    private int[] detect(World world, File folder) {
        File[] regions = new File(folder, "region").listFiles((d, n) -> REGION.matcher(n).matches());
        if (regions == null) {
            return null;
        }
        int[] box = null;
        var worlds = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().worlds();
        int bottom = worlds.minHeight(world);
        for (File region : regions) {
            Matcher m = REGION.matcher(region.getName());
            if (!m.matches() || region.length() == 0) {
                continue;
            }
            int rx = Integer.parseInt(m.group(1));
            int rz = Integer.parseInt(m.group(2));
            for (int cx = rx * 32; cx < rx * 32 + 32; cx++) {
                for (int cz = rz * 32; cz < rz * 32 + 32; cz++) {
                    if (!worlds.isChunkGenerated(world, cx, cz)) {
                        continue;
                    }
                    ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot();
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int top = snap.getHighestBlockYAt(x, z);
                            if (top <= bottom || worlds.isAir(snap, x, top, z)) {
                                continue;
                            }
                            int low = top;
                            for (int y = bottom; y < top; y++) {
                                if (!worlds.isAir(snap, x, y, z)) {
                                    low = y;
                                    break;
                                }
                            }
                            int wx = cx * 16 + x;
                            int wz = cz * 16 + z;
                            if (box == null) {
                                box = new int[] {wx, low, wz, wx, top, wz};
                            } else {
                                box[0] = Math.min(box[0], wx);
                                box[1] = Math.min(box[1], low);
                                box[2] = Math.min(box[2], wz);
                                box[3] = Math.max(box[3], wx);
                                box[4] = Math.max(box[4], top);
                                box[5] = Math.max(box[5], wz);
                            }
                        }
                    }
                }
            }
        }
        return box;
    }

    private void writeEntry(GameId game, String mapId, YamlConfiguration points, int[] min) throws IOException {
        Path file = persistence.mapsBaseDir().resolve(game.value() + ".yml");
        YamlConfiguration yaml = Files.isRegularFile(file)
                ? YamlConfiguration.loadConfiguration(file.toFile()) : new YamlConfiguration();
        yaml.set("maps." + mapId, null);
        ConfigurationSection out = yaml.createSection("maps." + mapId);
        for (String key : points.getKeys(false)) {
            if (key.equals("bounds")) {
                continue;
            }
            out.set(key, shift(points.get(key), min));
        }
        out.set("schematic-file", mapId + "." + SchematicStore.current().extension());
        yaml.save(file.toFile());
    }

    /** Copies a value, subtracting the min corner from x, y and z wherever they appear. */
    private static Object shift(Object value, int[] min) {
        if (value instanceof ConfigurationSection s) {
            return shift(s.getValues(false), min);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (v instanceof Number n && (k.equals("x") || k.equals("y") || k.equals("z"))) {
                    int axis = k.equals("x") ? 0 : k.equals("y") ? 1 : 2;
                    copy.put(k, n.doubleValue() - min[axis]);
                } else {
                    copy.put(k, shift(v, min));
                }
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object o : list) {
                copy.add(shift(o, min));
            }
            return copy;
        }
        return value;
    }
}
