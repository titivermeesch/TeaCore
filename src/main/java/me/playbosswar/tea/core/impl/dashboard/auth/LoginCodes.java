package me.playbosswar.tea.core.impl.dashboard.auth;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Codes are created on whatever server the player is on and redeemed by the dashboard,
 * possibly on another server, through the shared database. A code stores the player's
 * dashboard permissions because the dashboard server can't check permissions of a
 * player who isn't online there.
 */
public final class LoginCodes {

    public static final Duration LIFETIME = Duration.ofMinutes(2);
    /** No 0/O, 1/I/L: easy to read and type. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final DataSource dataSource;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();

    public record Grant(UUID uuid, String name, Set<String> permissions) { }

    public LoginCodes(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public String newCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Blocking; call off the main thread. */
    public void store(String code, UUID uuid, String name, List<String> permissions) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM tea_dashboard_logins WHERE uuid = ? OR expires_at < ?")) {
                del.setString(1, uuid.toString());
                del.setTimestamp(2, Timestamp.from(Instant.now()));
                del.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_dashboard_logins "
                    + "(code, uuid, name, permissions, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, normalize(code));
                ps.setString(2, uuid.toString());
                ps.setString(3, name);
                ps.setString(4, String.join(",", permissions));
                ps.setTimestamp(5, Timestamp.from(Instant.now().plus(LIFETIME)));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not store dashboard login code", e);
            throw new IllegalStateException("database error", e);
        }
    }

    public Optional<Grant> redeem(String code) {
        String key = normalize(code);
        if (key.length() != 8) {
            return Optional.empty();
        }
        try (Connection c = dataSource.getConnection()) {
            Grant grant = null;
            Instant expires = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT uuid, name, permissions, expires_at FROM tea_dashboard_logins WHERE code = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Set<String> perms = new LinkedHashSet<>();
                        for (String p : rs.getString("permissions").split(",")) {
                            if (!p.isBlank()) {
                                perms.add(p.trim());
                            }
                        }
                        grant = new Grant(UUID.fromString(rs.getString("uuid")), rs.getString("name"), perms);
                        expires = rs.getTimestamp("expires_at").toInstant();
                    }
                }
            }
            if (grant == null) {
                return Optional.empty();
            }
            try (PreparedStatement del = c.prepareStatement("DELETE FROM tea_dashboard_logins WHERE code = ?")) {
                del.setString(1, key);
                // Only the request that actually deletes the row gets the session.
                if (del.executeUpdate() == 0) {
                    return Optional.empty();
                }
            }
            return expires.isBefore(Instant.now()) ? Optional.empty() : Optional.of(grant);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not redeem dashboard login code", e);
            return Optional.empty();
        }
    }

    static String normalize(String code) {
        StringBuilder sb = new StringBuilder();
        for (char ch : code.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static List<String> granted(java.util.function.Predicate<String> has) {
        List<String> out = new ArrayList<>();
        for (String node : DashboardPermission.ALL) {
            if (has.test(node)) {
                out.add(node);
            }
        }
        return out;
    }
}
