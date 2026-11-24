package me.playbosswar.tea.core.impl.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.persistence.DatabaseService;
import me.playbosswar.tea.core.impl.config.CoreConfig;
import org.bukkit.plugin.Plugin;

public final class DatabaseServiceImpl implements DatabaseService, AutoCloseable {

    private final HikariDataSource pool;
    private final MigrationRunner runner;

    public DatabaseServiceImpl(CoreConfig.DatabaseConfig config, Logger logger) {
        HikariConfig hc = new HikariConfig();
        switch (config.type()) {
            case SQLITE -> {
                Path path = config.sqliteFile().toAbsolutePath();
                try {
                    Path parent = path.getParent();
                    if (parent != null)
                        Files.createDirectories(parent);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create SQLite parent dir: " + path.getParent(), e);
                }
                hc.setJdbcUrl("jdbc:sqlite:" + path);
                hc.setDriverClassName(org.sqlite.JDBC.class.getName());
                hc.setMaximumPoolSize(4);
                hc.setConnectionTestQuery("SELECT 1");
            }
            case MYSQL -> {
                hc.setJdbcUrl("jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort()
                        + "/" + config.mysqlDatabase() + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
                hc.setDriverClassName(com.mysql.cj.jdbc.Driver.class.getName());
                hc.setUsername(config.mysqlUser());
                hc.setPassword(config.mysqlPassword());
                hc.setMaximumPoolSize(10);
            }
        }
        hc.setPoolName("TeaCore-Hikari");
        this.pool = new HikariDataSource(hc);
        this.runner = new MigrationRunner(pool, logger);
    }

    @Override
    public DataSource dataSource() {
        return pool;
    }

    @Override
    public void registerMigrations(Plugin plugin, String namespace, List<String> filenames) {
        runner.apply(plugin, namespace, filenames);
    }

    @Override
    public void close() {
        pool.close();
    }
}
