package me.playbosswar.tea.core.impl.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.bukkit.plugin.Plugin;

public final class MigrationRunner {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private final DataSource dataSource;
    private final Logger logger;

    public MigrationRunner(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    private static final int LOCK_TIMEOUT_SECONDS = 120;

    public void apply(Plugin plugin, String namespace, List<String> filenames) {
        ensureVersionsTable();
        // Servers sharing one MySQL database can start together. A named lock, held on
        // its own connection for the whole run, makes them take turns; the waiter
        // re-reads what is applied so nothing runs twice. SQLite needs no lock.
        try (Connection lock = dataSource.getConnection()) {
            boolean mysql = isMySql(lock);
            if (mysql) {
                acquire(lock, namespace);
            }
            try {
                applyUnlocked(plugin, namespace, filenames);
            } finally {
                if (mysql) {
                    release(lock);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run migrations for " + namespace, e);
        }
    }

    private static boolean isMySql(Connection c) throws SQLException {
        String product = c.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT);
        return product.contains("mysql") || product.contains("mariadb");
    }

    private void acquire(Connection c, String namespace) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT GET_LOCK('tea_migrations', ?)")) {
            ps.setInt(1, LOCK_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new SQLException("Timed out after " + LOCK_TIMEOUT_SECONDS
                            + "s waiting for another server's migrations (" + namespace + ")");
                }
            }
        }
    }

    private void release(Connection c) {
        try (Statement s = c.createStatement()) {
            s.execute("SELECT RELEASE_LOCK('tea_migrations')");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not release the migration lock", e);
        }
    }

    private void applyUnlocked(Plugin plugin, String namespace, List<String> filenames) {
        Set<Integer> applied = loadAppliedVersions(namespace);

        List<MigrationFile> sorted = filenames.stream()
                .map(MigrationRunner::parse)
                .sorted(Comparator.comparingInt(MigrationFile::version))
                .toList();

        for (MigrationFile m : sorted) {
            if (applied.contains(m.version()))
                continue;
            String sql = readMigrationSql(plugin, namespace, m.filename());
            applyMigration(namespace, m.version(), sql);
            logger.info("Applied migration [" + namespace + "] " + m.filename());
        }
    }

    private static MigrationFile parse(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid migration filename '" + filename + "'; expected V<int>__<desc>.sql");
        }
        return new MigrationFile(Integer.parseInt(matcher.group(1)), filename);
    }

    private void ensureVersionsTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS tea_schema_versions ("
                + "  plugin_name VARCHAR(64) NOT NULL,"
                + "  version     INT NOT NULL,"
                + "  applied_at  TIMESTAMP NOT NULL,"
                + "  PRIMARY KEY (plugin_name, version))";
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure tea_schema_versions table", e);
        }
    }

    private Set<Integer> loadAppliedVersions(String namespace) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT version FROM tea_schema_versions WHERE plugin_name = ?")) {
            ps.setString(1, namespace);
            Set<Integer> out = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    out.add(rs.getInt(1));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load applied migration versions", e);
        }
    }

    private String readMigrationSql(Plugin plugin, String namespace, String filename) {
        String path = "db/migrations/" + namespace + "/" + filename;
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing migration resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read migration " + path, e);
        }
    }

    private void applyMigration(String namespace, int version, String sql) {
        try (Connection c = dataSource.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement s = c.createStatement()) {
                    for (String stmt : splitStatements(sql)) {
                        if (!stmt.isBlank())
                            s.executeUpdate(stmt);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tea_schema_versions (plugin_name, version, applied_at) VALUES (?, ?, ?)")) {
                    ps.setString(1, namespace);
                    ps.setInt(2, version);
                    ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Rollback failed for " + namespace + " V" + version, rollbackEx);
                }
                throw e;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to apply migration " + namespace + " V" + version, e);
        }
    }

    private static List<String> splitStatements(String sql) {
        return Arrays.stream(sql.split(";\\s*\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private record MigrationFile(int version, String filename) {
    }
}
