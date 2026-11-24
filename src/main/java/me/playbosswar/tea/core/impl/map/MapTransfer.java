package me.playbosswar.tea.core.impl.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.settings.SettingsSchema;
import me.playbosswar.tea.core.impl.settings.YamlPatcher;
import org.jetbrains.annotations.Nullable;

/**
 * Maps and waiting rooms as zip files, laid out like {@code plugins/TeaCore}:
 * {@code maps/<game>.yml}, {@code maps/schems/...} and {@code waiting-rooms/...}.
 * Importing merges: maps and rooms in the zip replace those with the same id,
 * everything else stays.
 */
public final class MapTransfer {

    /** What an import brought in. */
    public record Imported(List<String> maps, List<String> rooms) { }

    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    private static final Pattern GAME_FILE = Pattern.compile("maps/([a-z0-9_-]+)\\.yml");
    private static final Pattern SCHEM = Pattern.compile("(maps/schems|waiting-rooms)/[A-Za-z0-9_.-]+\\.(schem|schematic)");

    private final Path teaFolder;

    public MapTransfer(Path teaFolder) {
        this.teaFolder = teaFolder;
    }

    /** One map: its entry in the game's file and its build in every format present. */
    public byte[] exportMap(GameId game, String mapId) throws IOException {
        Path gameFile = teaFolder.resolve("maps").resolve(game.value() + ".yml");
        Object doc = Files.isRegularFile(gameFile) ? YamlPatcher.load(read(gameFile)) : null;
        Object entry = doc instanceof Map<?, ?> m && m.get("maps") instanceof Map<?, ?> maps ? maps.get(mapId) : null;
        if (!(entry instanceof Map<?, ?> entryMap)) {
            throw new IOException("No map " + game.value() + "/" + mapId);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            String yaml = YamlPatcher.set("", "maps." + mapId, SettingsSchema.copy(entryMap));
            put(zip, "maps/" + game.value() + ".yml", yaml.getBytes(StandardCharsets.UTF_8));
            Object schematic = entryMap.get("schematic-file");
            String base = schematic == null ? mapId : String.valueOf(schematic).replaceFirst("\\.[^.]+$", "");
            for (String ext : List.of("schem", "schematic")) {
                Path p = teaFolder.resolve("maps/schems").resolve(base + "." + ext);
                if (Files.isRegularFile(p)) {
                    put(zip, "maps/schems/" + base + "." + ext, Files.readAllBytes(p));
                }
            }
        }
        return bytes.toByteArray();
    }

    /** Every map and waiting room, without older map versions. */
    public byte[] exportAll() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String root : List.of("maps", "waiting-rooms")) {
                Path dir = teaFolder.resolve(root);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path p : (Iterable<Path>) walk::iterator) {
                        String rel = teaFolder.relativize(p).toString().replace('\\', '/');
                        if (Files.isRegularFile(p) && !rel.startsWith("maps/backups/") && !rel.endsWith(".tmp")) {
                            put(zip, rel, Files.readAllBytes(p));
                        }
                    }
                }
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Writes the zip's maps and rooms into the folders. {@code beforeReplace} runs
     * for each map that already exists, so it can be backed up first.
     *
     * @throws IOException when the zip holds nothing TeaCore can use or a file it can't take
     */
    public Imported importZip(byte[] data, @Nullable MapBackupHook beforeReplace) throws IOException {
        Map<String, byte[]> files = unzip(data);
        List<String> maps = new ArrayList<>();
        List<String> rooms = new ArrayList<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            String name = f.getKey();
            if (SCHEM.matcher(name).matches()) {
                Path target = teaFolder.resolve(name);
                Files.createDirectories(target.getParent());
                Files.write(target, f.getValue());
            }
        }
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            var game = GAME_FILE.matcher(f.getKey());
            if (!game.matches()) {
                continue;
            }
            Object doc = YamlPatcher.load(new String(f.getValue(), StandardCharsets.UTF_8));
            if (!(doc instanceof Map<?, ?> d) || !(d.get("maps") instanceof Map<?, ?> entries)) {
                continue;
            }
            Path target = teaFolder.resolve("maps").resolve(game.group(1) + ".yml");
            String text = Files.isRegularFile(target) ? read(target) : "maps: {}\n";
            Object current = YamlPatcher.load(text);
            for (Map.Entry<?, ?> e : entries.entrySet()) {
                String id = String.valueOf(e.getKey());
                if (!SettingsSchema.validEntryId(id) || !(e.getValue() instanceof Map<?, ?> entry)) {
                    throw new IOException("Bad map entry " + id + " in " + f.getKey());
                }
                boolean exists = current instanceof Map<?, ?> cm && cm.get("maps") instanceof Map<?, ?> cmaps
                        && cmaps.containsKey(id);
                if (exists && beforeReplace != null) {
                    beforeReplace.backup(GameId.of(game.group(1)), id);
                }
                text = YamlPatcher.set(text, "maps." + id, SettingsSchema.copy(entry));
                maps.add(game.group(1) + "/" + id);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, text.getBytes(StandardCharsets.UTF_8));
        }
        byte[] roomFile = files.get("waiting-rooms/waiting-rooms.yml");
        if (roomFile != null && YamlPatcher.load(new String(roomFile, StandardCharsets.UTF_8)) instanceof Map<?, ?> in) {
            Path target = teaFolder.resolve("waiting-rooms/waiting-rooms.yml");
            String text = Files.isRegularFile(target) ? read(target) : "";
            if (in.get("rooms") instanceof Map<?, ?> r) {
                for (Map.Entry<?, ?> e : r.entrySet()) {
                    text = YamlPatcher.set(text, "rooms." + e.getKey(), e.getValue());
                    rooms.add(String.valueOf(e.getKey()));
                }
            }
            if (in.get("games") instanceof Map<?, ?> g) {
                for (Map.Entry<?, ?> e : g.entrySet()) {
                    text = YamlPatcher.set(text, "games." + e.getKey(), e.getValue());
                }
            }
            if (in.get("maps") instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> perMap) {
                        for (Map.Entry<?, ?> pm : perMap.entrySet()) {
                            text = YamlPatcher.set(text, "maps." + e.getKey() + "." + pm.getKey(), pm.getValue());
                        }
                    }
                }
            }
            Files.createDirectories(target.getParent());
            Files.write(target, text.getBytes(StandardCharsets.UTF_8));
        }
        if (maps.isEmpty() && rooms.isEmpty()) {
            throw new IOException("The zip has no maps or waiting rooms (expected maps/<game>.yml)");
        }
        return new Imported(maps, rooms);
    }

    @FunctionalInterface
    public interface MapBackupHook {
        void backup(GameId game, String mapId);
    }

    private static Map<String, byte[]> unzip(byte[] data) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName().replace('\\', '/');
                while (name.startsWith("./")) {
                    name = name.substring(2);
                }
                name = normalize(name);
                if (name.contains("..") || name.startsWith("/")) {
                    throw new IOException("Refusing " + e.getName() + " in the zip");
                }
                byte[] bytes = zip.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, MAX_TOTAL - total + 1));
                total += bytes.length;
                if (total > MAX_TOTAL) {
                    throw new IOException("The zip is larger than 256 MB unpacked");
                }
                out.put(name, bytes);
            }
        }
        return out;
    }

    /**
     * Accepts zips of the {@code plugins/TeaCore} folder, of its {@code maps}
     * folder, or a game's loose map files such as Overcharge's {@code maps/} folder.
     */
    static String normalize(String name) {
        for (String prefix : List.of("plugins/", "TeaCore/")) {
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
            }
        }
        if (name.startsWith("schems/")) {
            return "maps/" + name;
        }
        if (!name.contains("/")) {
            if (name.endsWith(".yml") && !name.equals("waiting-rooms.yml")) {
                return "maps/" + name;
            }
            if (name.endsWith(".schem") || name.endsWith(".schematic")) {
                return "maps/schems/" + name;
            }
        }
        return name;
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
