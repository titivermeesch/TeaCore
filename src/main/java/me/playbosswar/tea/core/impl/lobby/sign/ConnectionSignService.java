package me.playbosswar.tea.core.impl.lobby.sign;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Holds bound connection signs in memory, backed by {@code tea_lobby_signs}.
 */
public final class ConnectionSignService {

    private final DataSource dataSource;
    private final Logger logger;
    private final ConcurrentHashMap<String, ConnectionSign> signs = new ConcurrentHashMap<>();

    public ConnectionSignService(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public void loadAll() {
        signs.clear();
        String sql = "SELECT world, x, y, z, game_id, map_filter FROM tea_lobby_signs";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ConnectionSign sign = new ConnectionSign(
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        GameId.of(rs.getString("game_id")),
                        rs.getString("map_filter"));
                signs.put(sign.key(), sign);
            }
            logger.info("Loaded " + signs.size() + " connection sign(s) from DB");
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load connection signs", e);
        }
    }

    public Optional<ConnectionSign> get(String world, int x, int y, int z) {
        return Optional.ofNullable(signs.get(ConnectionSign.key(world, x, y, z)));
    }

    public Collection<ConnectionSign> all() {
        return Collections.unmodifiableCollection(signs.values());
    }

    public void bind(ConnectionSign sign) {
        String update = "UPDATE tea_lobby_signs SET game_id = ?, map_filter = ? "
                + "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        String insert = "INSERT INTO tea_lobby_signs (world, x, y, z, game_id, map_filter) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, sign.gameId().value());
                ps.setString(2, sign.mapFilter());
                ps.setString(3, sign.world());
                ps.setInt(4, sign.x());
                ps.setInt(5, sign.y());
                ps.setInt(6, sign.z());
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement is = c.prepareStatement(insert)) {
                        is.setString(1, sign.world());
                        is.setInt(2, sign.x());
                        is.setInt(3, sign.y());
                        is.setInt(4, sign.z());
                        is.setString(5, sign.gameId().value());
                        is.setString(6, sign.mapFilter());
                        is.executeUpdate();
                    }
                }
            }
            signs.put(sign.key(), sign);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to bind sign " + sign.key(), e);
        }
    }

    public boolean unbind(String world, int x, int y, int z) {
        ConnectionSign removed = signs.remove(ConnectionSign.key(world, x, y, z));
        if (removed == null)
            return false;
        String sql = "DELETE FROM tea_lobby_signs WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to unbind sign " + ConnectionSign.key(world, x, y, z), e);
        }
        return true;
    }
}
