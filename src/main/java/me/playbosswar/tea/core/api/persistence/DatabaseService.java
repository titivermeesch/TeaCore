package me.playbosswar.tea.core.api.persistence;

import java.util.List;
import javax.sql.DataSource;
import org.bukkit.plugin.Plugin;

/**
 * Shared JDBC pool and per-plugin schema migrations, tracked in the
 * {@code tea_schema_versions} table.
 */
public interface DatabaseService {

    /** SQLite or MySQL depending on config. Pool owned by TeaCore: close connections, never the pool. */
    DataSource dataSource();

    /**
     * Applies pending migrations synchronously, skipping versions already applied.
     * Files must be named {@code V<int>__<description>.sql} and live under
     * {@code /db/migrations/<namespace>/} in the plugin jar.
     *
     * @throws IllegalStateException if a migration fails
     */
    void registerMigrations(Plugin plugin, String namespace, List<String> migrationFilenames);
}
