package me.playbosswar.tea.core.impl.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.stats.StatsProvider;
import me.playbosswar.tea.core.api.stats.StatsSchema;

/** Table and column names come from a game plugin's schema, not user input, so concatenating them into SQL is safe. */
public final class DbStatsProvider implements StatsProvider {

    private final StatsSchema schema;
    private final DataSource dataSource;
    private final Logger logger;
    private final String query;

    public DbStatsProvider(StatsSchema schema, DataSource dataSource, Logger logger) {
        this.schema = schema;
        this.dataSource = dataSource;
        this.logger = logger;
        this.query = buildQuery(schema);
    }

    @Override
    public GameId gameId() {
        return schema.gameId();
    }

    @Override
    public Map<String, String> fetch(UUID playerUuid) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Map.of();
                Map<String, String> entries = StatsProvider.newEntries();
                int i = 1;
                for (StatsSchema.Column column : schema.columns()) {
                    entries.put(column.label(), rs.getString(i++));
                }
                return entries;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to load " + schema.gameId().value()
                    + " stats for " + playerUuid, e);
            return Map.of();
        }
    }

    private static String buildQuery(StatsSchema schema) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < schema.columns().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(schema.columns().get(i).sqlColumn());
        }
        sb.append(" FROM ").append(schema.sqlTable())
                .append(" WHERE ").append(schema.sqlUuidColumn()).append(" = ?");
        return sb.toString();
    }
}
