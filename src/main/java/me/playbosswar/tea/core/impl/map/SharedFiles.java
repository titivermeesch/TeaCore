package me.playbosswar.tea.core.impl.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 * Keeps {@code plugins/TeaCore/maps} and {@code waiting-rooms} the same on every
 * server through the database. The folders stay the working copy the map code
 * reads and writes; the database holds the shared version.
 *
 * <p>A server pulls at startup and whenever another server announces a change,
 * and pushes after its own saves. Only files that changed since the last sync
 * move, so two staff editing different maps don't overwrite each other.
 */
public final class SharedFiles {

    static final List<String> ROOTS = List.of("maps", "waiting-rooms");
    private static final int PART = 1024 * 1024;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final DataSource db;
    private final Path base;
    private final String serverId;
    private final Logger logger;
    /** Path -> hash as of the last pull or push. */
    private final Map<String, String> synced = new HashMap<>();

    public SharedFiles(DataSource db, Path dataFolder, String serverId, Logger logger) {
        this.db = db;
        this.base = dataFolder;
        this.serverId = serverId;
        this.logger = logger;
    }

    /**
     * Brings the local folders up to date. The first server to run with an empty
     * database uploads its files instead.
     *
     * @return the paths that changed locally
     */
    public Set<String> pull() {
        return pull(false);
    }

    /**
     * At startup a local file the database doesn't know came with the server, for
     * example in its image, and is set aside. Later such a file is a new local
     * map that the next push uploads.
     */
    public Set<String> pullAtStartup() {
        return pull(true);
    }

    private synchronized Set<String> pull(boolean startup) {
        Set<String> changed = new LinkedHashSet<>();
        try (Connection c = db.getConnection()) {
            Map<String, String> remote = listRemote(c);
            Map<String, String> local = scanLocal();
            if (remote.isEmpty()) {
                if (!local.isEmpty()) {
                    logger.info("Uploading " + local.size() + " map and waiting room file(s) to the database.");
                    synced.clear();
                    pushChanges(c, local, remote);
                }
                return changed;
            }
            Path setAside = null;
            for (Map.Entry<String, String> e : remote.entrySet()) {
                String path = e.getKey();
                String localHash = local.get(path);
                if (e.getValue().equals(localHash)) {
                    synced.put(path, localHash);
                    continue;
                }
                String known = synced.get(path);
                if (localHash != null && known != null && !localHash.equals(known)) {
                    if (!e.getValue().equals(known)) {
                        logger.warning(path + " changed here and on another server; keeping this server's copy.");
                    }
                    // Changed here and not pushed yet: the push that follows uploads it.
                    continue;
                }
                byte[] data = download(c, e.getValue());
                if (data == null) {
                    logger.warning("The database has no content for " + path + "; keeping the local copy.");
                    continue;
                }
                writeAtomically(resolve(path), data);
                synced.put(path, e.getValue());
                changed.add(path);
            }
            for (String path : local.keySet()) {
                if (remote.containsKey(path)) {
                    continue;
                }
                String known = synced.get(path);
                if (known != null && known.equals(local.get(path))) {
                    Files.deleteIfExists(resolve(path));
                    synced.remove(path);
                    changed.add(path);
                } else if (known == null && startup) {
                    // A file this server never shared, for example one baked into its image.
                    if (setAside == null) {
                        setAside = base.resolve("replaced-by-database").resolve(STAMP.format(Instant.now()));
                    }
                    Path target = setAside.resolve(path);
                    Files.createDirectories(target.getParent());
                    Files.move(resolve(path), target, StandardCopyOption.REPLACE_EXISTING);
                    synced.remove(path);
                    changed.add(path);
                }
            }
            if (setAside != null) {
                logger.info("Moved map files the database doesn't have to " + base.relativize(setAside));
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not load maps from the database; using the local files", e);
        }
        return changed;
    }

    /**
     * Uploads what changed here since the last sync.
     *
     * @return whether anything was uploaded or removed
     */
    public synchronized boolean push() {
        try (Connection c = db.getConnection()) {
            return pushChanges(c, scanLocal(), listRemote(c));
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not save maps to the database", e);
            return false;
        }
    }

    private boolean pushChanges(Connection c, Map<String, String> local, Map<String, String> remote)
            throws SQLException, IOException {
        boolean any = false;
        for (Map.Entry<String, String> e : local.entrySet()) {
            String path = e.getKey();
            String hash = e.getValue();
            if (hash.equals(synced.get(path)) || hash.equals(remote.get(path))) {
                synced.put(path, hash);
                continue;
            }
            byte[] data = Files.readAllBytes(resolve(path));
            upload(c, hash, data);
            upsertFile(c, path, hash, data.length);
            synced.put(path, hash);
            any = true;
        }
        for (String path : List.copyOf(synced.keySet())) {
            if (!local.containsKey(path)) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM tea_files WHERE path = ?")) {
                    ps.setString(1, path);
                    ps.executeUpdate();
                }
                synced.remove(path);
                any = true;
            }
        }
        if (any) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM tea_file_chunks WHERE hash NOT IN (SELECT hash FROM tea_files)")) {
                ps.executeUpdate();
            }
        }
        return any;
    }

    // ------------------------------------------------------------------ database

    private static Map<String, String> listRemote(Connection c) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT path, hash FROM tea_files");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }

    private static byte[] download(Connection c, String hash) throws SQLException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean found = false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT data FROM tea_file_chunks WHERE hash = ? ORDER BY part")) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.write(rs.getBytes(1));
                    found = true;
                }
            }
        }
        byte[] data = out.toByteArray();
        return found && hash.equals(hash(data)) ? data : null;
    }

    private static void upload(Connection c, String hash, byte[] data) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM tea_file_chunks WHERE hash = ? AND part = 0")) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tea_file_chunks (hash, part, data) VALUES (?, ?, ?)")) {
            int part = 0;
            for (int off = 0; off < data.length || part == 0; off += PART, part++) {
                int len = Math.min(PART, data.length - off);
                byte[] chunk = new byte[Math.max(0, len)];
                System.arraycopy(data, off, chunk, 0, chunk.length);
                ps.setString(1, hash);
                ps.setInt(2, part);
                ps.setBytes(3, chunk);
                ps.executeUpdate();
            }
        }
    }

    private void upsertFile(Connection c, String path, String hash, long size) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE tea_files SET hash = ?, size = ?, updated_at = ?, updated_by = ? WHERE path = ?")) {
            ps.setString(1, hash);
            ps.setLong(2, size);
            ps.setLong(3, now);
            ps.setString(4, serverId);
            ps.setString(5, path);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tea_files (path, hash, size, updated_at, updated_by) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, path);
            ps.setString(2, hash);
            ps.setLong(3, size);
            ps.setLong(4, now);
            ps.setString(5, serverId);
            ps.executeUpdate();
        }
    }

    // --------------------------------------------------------------------- local

    private Map<String, String> scanLocal() throws IOException {
        Map<String, String> out = new HashMap<>();
        for (String root : ROOTS) {
            Path dir = base.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    String name = p.getFileName().toString();
                    if (!Files.isRegularFile(p) || name.endsWith(".tmp") || name.startsWith(".")) {
                        continue;
                    }
                    out.put(base.relativize(p).toString().replace('\\', '/'), hash(Files.readAllBytes(p)));
                }
            }
        }
        return out;
    }

    private Path resolve(String path) throws IOException {
        Path p = base.resolve(path).normalize();
        boolean inside = ROOTS.stream().anyMatch(r -> p.startsWith(base.resolve(r).normalize()));
        if (!inside) {
            throw new IOException("Refusing a shared file outside the map folders: " + path);
        }
        return p;
    }

    private static void writeAtomically(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
