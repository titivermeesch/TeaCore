package me.playbosswar.tea.core.impl.coordination;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import javax.sql.DataSource;

/**
 * The key servers sign their Redis messages with. Unless {@code network.secret}
 * is set, the first server to start generates one and stores it in the shared
 * database, and every other server reads it from there.
 */
public final class NetworkSecret {

    private static final String NAME = "secret";

    private NetworkSecret() {
    }

    public static byte[] resolve(DataSource dataSource, String override) throws SQLException {
        if (override != null && !override.isBlank()) {
            return override.trim().getBytes(StandardCharsets.UTF_8);
        }
        String stored = read(dataSource);
        if (stored == null) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO tea_network (name, value) VALUES (?, ?)")) {
                ps.setString(1, NAME);
                ps.setString(2, Base64.getEncoder().encodeToString(random));
                ps.executeUpdate();
            } catch (SQLException e) {
                // Another server inserted it first; theirs wins.
            }
            stored = read(dataSource);
            if (stored == null) {
                throw new SQLException("Could not store the network secret");
            }
        }
        return stored.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(DataSource dataSource) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM tea_network WHERE name = ?")) {
            ps.setString(1, NAME);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
