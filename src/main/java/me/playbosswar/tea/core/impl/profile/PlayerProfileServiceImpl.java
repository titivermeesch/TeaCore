package me.playbosswar.tea.core.impl.profile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.profile.PlayerProfile;
import me.playbosswar.tea.core.api.profile.PlayerProfileService;

public final class PlayerProfileServiceImpl implements PlayerProfileService {

    private final DataSource dataSource;
    private final Logger logger;
    private final Cache<UUID, PlayerProfileImpl> cache = Caffeine.newBuilder()
            .maximumSize(1024)
            .build();

    public PlayerProfileServiceImpl(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    @Override
    public PlayerProfile getOrCreate(UUID uuid) {
        return cache.get(uuid, this::loadOrCreate);
    }

    @Override
    public Optional<PlayerProfile> get(UUID uuid) {
        return Optional.ofNullable(cache.getIfPresent(uuid));
    }

    public void persist(PlayerProfileImpl profile) {
        upsert(profile);
    }

    private PlayerProfileImpl loadOrCreate(UUID uuid) {
        PlayerProfileImpl existing = loadFromDb(uuid);
        if (existing != null)
            return existing;

        Instant now = Instant.now();
        PlayerProfileImpl fresh = new PlayerProfileImpl(uuid, now, now, null);
        upsert(fresh);
        return fresh;
    }

    private PlayerProfileImpl loadFromDb(UUID uuid) {
        String sql = "SELECT first_seen, last_seen, locale FROM tea_player_profile WHERE uuid = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerProfileImpl(
                            uuid,
                            rs.getTimestamp("first_seen").toInstant(),
                            rs.getTimestamp("last_seen").toInstant(),
                            rs.getString("locale"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load profile " + uuid, e);
        }
        return null;
    }

    private void upsert(PlayerProfileImpl profile) {
        String update = "UPDATE tea_player_profile SET last_seen = ?, locale = ? WHERE uuid = ?";
        String insert = "INSERT INTO tea_player_profile (uuid, first_seen, last_seen, locale) VALUES (?, ?, ?, ?)";

        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setTimestamp(1, Timestamp.from(profile.lastSeen()));
                ps.setString(2, profile.locale());
                ps.setString(3, profile.uuid().toString());
                if (ps.executeUpdate() > 0)
                    return;
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, profile.uuid().toString());
                ps.setTimestamp(2, Timestamp.from(profile.firstSeen()));
                ps.setTimestamp(3, Timestamp.from(profile.lastSeen()));
                ps.setString(4, profile.locale());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to upsert profile " + profile.uuid(), e);
        }
    }
}
