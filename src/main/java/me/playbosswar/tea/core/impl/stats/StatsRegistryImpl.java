package me.playbosswar.tea.core.impl.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.stats.StatsProvider;
import me.playbosswar.tea.core.api.stats.StatsRegistry;
import me.playbosswar.tea.core.api.stats.StatsSchema;

public final class StatsRegistryImpl implements StatsRegistry {

    private final ConcurrentHashMap<GameId, StatsProvider> providers = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final Logger logger;

    public StatsRegistryImpl(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    @Override
    public void register(StatsProvider provider) {
        providers.put(provider.gameId(), provider);
    }

    @Override
    public void unregister(GameId gameId) {
        providers.remove(gameId);
    }

    @Override
    public Optional<StatsProvider> forGame(GameId gameId) {
        return Optional.ofNullable(providers.get(gameId));
    }

    @Override
    public StatsProvider providerFor(StatsSchema schema) {
        StatsProvider direct = providers.get(schema.gameId());
        if (direct != null) return direct;
        return new DbStatsProvider(schema, dataSource, logger);
    }

    @Override
    public Collection<StatsProvider> all() {
        return Collections.unmodifiableCollection(providers.values());
    }

    @Override
    public void registerSchema(StatsSchema schema) {
        // DELETE-then-INSERT keeps the migration portable across SQLite and MySQL
        // without per-dialect upsert syntax. Wrapped in a transaction so a partial
        // failure doesn't leave the columns out of sync with the parent row.
        try (Connection c = dataSource.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM tea_stats_schema_columns WHERE game_id = ?")) {
                    del.setString(1, schema.gameId().value());
                    del.executeUpdate();
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM tea_stats_schemas WHERE game_id = ?")) {
                    del.setString(1, schema.gameId().value());
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO tea_stats_schemas "
                                + "(game_id, display_name_key, sql_table, sql_uuid_column) "
                                + "VALUES (?, ?, ?, ?)")) {
                    ins.setString(1, schema.gameId().value());
                    ins.setString(2, schema.displayNameKey());
                    ins.setString(3, schema.sqlTable());
                    ins.setString(4, schema.sqlUuidColumn());
                    ins.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO tea_stats_schema_columns "
                                + "(game_id, ordinal, label, sql_column) "
                                + "VALUES (?, ?, ?, ?)")) {
                    int ordinal = 0;
                    for (StatsSchema.Column col : schema.columns()) {
                        ins.setString(1, schema.gameId().value());
                        ins.setInt(2, ordinal++);
                        ins.setString(3, col.label());
                        ins.setString(4, col.sqlColumn());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE,
                    "Failed to persist stats schema for " + schema.gameId().value(), e);
        }
    }

    @Override
    public Collection<StatsSchema> allSchemas() {
        Map<String, SchemaBuilder> byGame = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT game_id, display_name_key, sql_table, sql_uuid_column "
                            + "FROM tea_stats_schemas ORDER BY game_id");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SchemaBuilder b = new SchemaBuilder();
                    b.gameId = rs.getString("game_id");
                    b.displayNameKey = rs.getString("display_name_key");
                    b.sqlTable = rs.getString("sql_table");
                    b.sqlUuidColumn = rs.getString("sql_uuid_column");
                    byGame.put(b.gameId, b);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT game_id, label, sql_column "
                            + "FROM tea_stats_schema_columns ORDER BY game_id, ordinal");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SchemaBuilder b = byGame.get(rs.getString("game_id"));
                    if (b == null) continue;
                    b.columns.add(new StatsSchema.Column(
                            rs.getString("label"), rs.getString("sql_column")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to read tea_stats_schemas", e);
            return List.of();
        }
        List<StatsSchema> out = new ArrayList<>(byGame.size());
        for (SchemaBuilder b : byGame.values()) {
            out.add(new StatsSchema(GameId.of(b.gameId), b.displayNameKey,
                    b.sqlTable, b.sqlUuidColumn, b.columns));
        }
        return Collections.unmodifiableList(out);
    }

    private static final class SchemaBuilder {
        String gameId;
        String displayNameKey;
        String sqlTable;
        String sqlUuidColumn;
        final List<StatsSchema.Column> columns = new ArrayList<>();
    }
}
