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
import java.util.HashMap;
import java.util.HashSet;
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
 * Keeps shared files, such as {@code plugins/TeaCore/maps} and the menus of every
 * Tea plugin, the same on every server through the database. The folders stay the working copy the map code
 * reads and writes; the database holds the shared version.
 *
 * <p>A server pulls at startup and whenever another server announces a change,
 * and pushes after its own saves. Only files that changed since the last sync
 * move, so two staff editing different maps don't overwrite each other.
 */
public final class SharedFiles {

    private static final int PART = 1024 * 1024;
    /** Unreferenced chunks younger than this may belong to an upload that has not committed yet. */
    private static final long CHUNK_GRACE_MS = 15 * 60 * 1000L;

    private final DataSource db;
    private final Path base;
    private final List<String> roots;
    private final String serverId;
    private final Logger logger;
    /**
     * Path -> hash as of the last pull or push. Kept on disk so that a file
     * edited while the server was off is recognised as a local change at the
     * next start instead of being replaced by the database copy.
     */
    private final Map<String, String> synced = new HashMap<>();
    private final Path stateFile;
    private static final String DB_LINE = "db ";
    private String dbIdentity;
    /** Hashes this server found without content and already asked the others to upload again. */
    private final Set<String> requested = new HashSet<>();
    private boolean announce;

    /**
     * @param pluginsFolder the server's {@code plugins} folder
     * @param roots         folders or files under it to share, such as {@code TeaCore/maps}
     */
    public SharedFiles(DataSource db, Path pluginsFolder, List<String> roots, String serverId, Logger logger) {
        this.db = db;
        this.base = pluginsFolder;
        this.roots = List.copyOf(roots);
        this.serverId = serverId;
        this.logger = logger;
        this.stateFile = pluginsFolder.resolve("TeaCore").resolve(".shared-files");
        loadState();
    }

    /**
     * Brings the local folders up to date. The first server to run with an empty
     * database uploads its files instead. A local file the database doesn't have,
     * or one edited here since the last sync, is left alone; the next push
     * uploads it.
     *
     * @return the paths that changed locally
     */
    public synchronized Set<String> pull() {
        try {
            return pullFiles();
        } finally {
            saveState();
        }
    }

    private Set<String> pullFiles() {
        Set<String> changed = new LinkedHashSet<>();
        try (Connection c = db.getConnection()) {
            Map<String, String> remote = listRemote(c);
            remote.keySet().removeIf(p -> !covered(p));
            Map<String, String> local = scanLocal();
            Set<String> stored = storedHashes(c);
            if (remote.isEmpty()) {
                if (!local.isEmpty()) {
                    logger.info("Uploading " + local.size() + " shared file(s) to the database.");
                    synced.clear();
                    pushChanges(c, local, remote);
                }
                return changed;
            }
            for (Map.Entry<String, String> e : remote.entrySet()) {
                String path = e.getKey();
                String localHash = local.get(path);
                if (e.getValue().equals(localHash)) {
                    synced.put(path, localHash);
                    if (!stored.contains(localHash)) {
                        byte[] data = Files.readAllBytes(resolve(path));
                        if (localHash.equals(hash(data))) {
                            logger.info("Re-uploading the missing content of " + path + ".");
                            inTransaction(c, () -> storeChunks(c, localHash, data));
                            stored.add(localHash);
                            announce = true;
                        }
                    }
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
                    logger.warning("The database has no content for " + path + "; keeping the local copy"
                            + " and asking the other servers to upload it again.");
                    if (requested.add(e.getValue())) {
                        announce = true;
                    }
                    continue;
                }
                requested.remove(e.getValue());
                writeAtomically(resolve(path), data);
                synced.put(path, e.getValue());
                changed.add(path);
            }
            for (String path : local.keySet()) {
                String known = synced.get(path);
                if (!remote.containsKey(path) && known != null && known.equals(local.get(path)) && covered(path)) {
                    // Deleted on another server since we last synced.
                    Files.deleteIfExists(resolve(path));
                    synced.remove(path);
                    changed.add(path);
                }
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not load maps from the database; using the local files", e);
        }
        return changed;
    }

    /**
     * Whether the last pull or push changed something the other servers should
     * hear about: content re-uploaded for a file whose chunks had gone missing,
     * or a file this server could not download because its content is missing.
     * Reading it clears it.
     */
    public synchronized boolean takeAnnouncement() {
        boolean out = announce;
        announce = false;
        return out;
    }

    /**
     * Uploads what changed here since the last sync.
     *
     * @return whether anything was uploaded or removed
     */
    public synchronized boolean push() {
        try (Connection c = db.getConnection()) {
            Map<String, String> remote = listRemote(c);
            remote.keySet().removeIf(p -> !covered(p));
            return pushChanges(c, scanLocal(), remote);
        } catch (SQLException | IOException e) {
            logger.log(Level.WARNING, "Could not save maps to the database", e);
            return false;
        } finally {
            saveState();
        }
    }

    private void loadState() {
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(stateFile);
            // State recorded against another database (e.g. before moving from SQLite to a shared
            // MySQL) says nothing about this one; trusting it would delete local files as "removed".
            if (lines.isEmpty() || !lines.get(0).equals(DB_LINE + databaseIdentity())) {
                return;
            }
            for (String line : lines.subList(1, lines.size())) {
                int space = line.indexOf(' ');
                if (space > 0) {
                    synced.put(line.substring(space + 1), line.substring(0, space));
                }
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not read " + stateFile, e);
        }
    }

    private String databaseIdentity() {
        if (dbIdentity == null) {
            try (Connection c = db.getConnection()) {
                String url = c.getMetaData().getURL();
                int query = url == null ? -1 : url.indexOf('?');
                dbIdentity = url == null ? "?" : query < 0 ? url : url.substring(0, query);
            } catch (SQLException e) {
                dbIdentity = "?";
            }
        }
        return dbIdentity;
    }

    private void saveState() {
        StringBuilder out = new StringBuilder(DB_LINE).append(databaseIdentity()).append('\n');
        synced.forEach((path, hash) -> out.append(hash).append(' ').append(path).append('\n'));
        try {
            writeAtomically(stateFile, out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not write " + stateFile, e);
        }
    }

    private boolean pushChanges(Connection c, Map<String, String> local, Map<String, String> remote)
            throws SQLException, IOException {
        boolean any = false;
        Set<String> stored = storedHashes(c);
        for (Map.Entry<String, String> e : local.entrySet()) {
            String path = e.getKey();
            String hash = e.getValue();
            boolean upToDate = hash.equals(remote.get(path)) ? stored.contains(hash) : hash.equals(synced.get(path));
            if (upToDate) {
                synced.put(path, hash);
                continue;
            }
            byte[] data = Files.readAllBytes(resolve(path));
            if (!hash.equals(hash(data))) {
                // Edited since the scan; the next push picks up the new content.
                continue;
            }
            inTransaction(c, () -> {
                storeChunks(c, hash, data);
                upsertFile(c, path, hash, data.length);
            });
            stored.add(hash);
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
                    "DELETE FROM tea_file_chunks WHERE touched_at < ? AND hash NOT IN (SELECT hash FROM tea_files)")) {
                ps.setLong(1, System.currentTimeMillis() - CHUNK_GRACE_MS);
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

    private static Set<String> storedHashes(Connection c) throws SQLException {
        Set<String> out = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT hash FROM tea_file_chunks WHERE part = 0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    /**
     * Writes the content for a hash, or marks existing content as just used so
     * cleanup on another server leaves it alone until the file row commits.
     */
    private static void storeChunks(Connection c, String hash, byte[] data) throws SQLException {
        long now = System.currentTimeMillis();
        int parts = Math.max(1, (data.length + PART - 1) / PART);
        try (PreparedStatement ps = c.prepareStatement("UPDATE tea_file_chunks SET touched_at = ? WHERE hash = ?")) {
            ps.setLong(1, now);
            ps.setString(2, hash);
            int existing = ps.executeUpdate();
            if (existing == parts) {
                return;
            }
            if (existing > 0) {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM tea_file_chunks WHERE hash = ?")) {
                    del.setString(1, hash);
                    del.executeUpdate();
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tea_file_chunks (hash, part, data, touched_at) VALUES (?, ?, ?, ?)")) {
            for (int part = 0; part < parts; part++) {
                int off = part * PART;
                int len = Math.max(0, Math.min(PART, data.length - off));
                byte[] chunk = new byte[len];
                System.arraycopy(data, off, chunk, 0, len);
                ps.setString(1, hash);
                ps.setInt(2, part);
                ps.setBytes(3, chunk);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
        }
    }

    private interface SqlWork {
        void run() throws SQLException;
    }

    private static void inTransaction(Connection c, SqlWork work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            work.run();
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            c.setAutoCommit(auto);
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
        for (String root : roots) {
            Path dir = base.resolve(root);
            if (Files.isRegularFile(dir)) {
                out.put(root, hash(Files.readAllBytes(dir)));
                continue;
            }
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

    private boolean covered(String path) {
        return roots.stream().anyMatch(r -> path.equals(r) || path.startsWith(r + "/"));
    }

    private Path resolve(String path) throws IOException {
        Path p = base.resolve(path).normalize();
        boolean inside = roots.stream().anyMatch(r -> p.startsWith(base.resolve(r).normalize()));
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
