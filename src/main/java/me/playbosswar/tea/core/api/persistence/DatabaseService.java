package me.playbosswar.tea.core.api.persistence;

import java.util.List;
import javax.sql.DataSource;
import org.bukkit.plugin.Plugin;

/**
 * Provides access to the shared JDBC connection pool and applies plugin schema
 * migrations.
 *
 * <p>
 * Migrations are bundled inside each plugin jar under
 * {@code /db/migrations/<namespace>/Vxxx__name.sql} and tracked against a
 * shared
 * {@code tea_schema_versions} table, so each plugin can evolve its schema
 * without knowing
 * about the others.
 */
public interface DatabaseService {

    /**
     * Returns the shared pooled JDBC {@link DataSource}.
     *
     * <p>
     * Resolves to SQLite or MySQL based on the server configuration. The pool is
     * reference-counted by TeaCore; callers should not close the underlying
     * connections
     * outside of normal try-with-resources scope.
     *
     * @return the data source, never {@code null}.
     */
    DataSource dataSource();

    /**
     * Applies any pending migrations for a plugin namespace.
     *
     * <p>
     * Already-applied versions (tracked in {@code tea_schema_versions}) are
     * skipped.
     * Filenames must match {@code V<int>__<description>.sql} and live under
     * {@code /db/migrations/<namespace>/} inside the plugin jar. Runs SQL
     * synchronously at
     * registration time and fails fast if any migration errors.
     *
     * @param plugin             plugin whose migrations to load; must not be
     *                           {@code null}.
     * @param namespace          subfolder under {@code /db/migrations/} to scan;
     *                           must not
     *                           be {@code null}.
     * @param migrationFilenames ordered list of migration filenames to attempt;
     *                           must not
     *                           be {@code null}.
     * @throws IllegalStateException if a migration fails to apply.
     */
    void registerMigrations(Plugin plugin, String namespace, List<String> migrationFilenames);
}
