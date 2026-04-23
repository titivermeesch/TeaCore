package me.playbosswar.tea.core.impl.config;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigLoader {

    /**
     * Known config keys. Anything outside this set generates a startup warning so
     * typos
     * don't silently fall through to defaults. Keep this in sync with the default
     * config.yml
     * and {@link CoreConfig}.
     */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "network",
            "network.mode",
            "network.server-id",
            "network.lobby-server",
            "redis",
            "redis.host",
            "redis.port",
            "redis.password",
            "database",
            "database.type",
            "database.sqlite-file",
            "database.mysql",
            "database.mysql.host",
            "database.mysql.port",
            "database.mysql.database",
            "database.mysql.user",
            "database.mysql.password",
            "i18n",
            "i18n.default-locale",
            "i18n.fallback-chain",
            "dashboard",
            "dashboard.enabled",
            "dashboard.bind-address",
            "dashboard.port",
            "dashboard.admin-token",
            "arena-pool",
            "arena-pool.mode",
            "arena-pool.games");

    private ConfigLoader() {
    }

    public static CoreConfig load(Path configFile, Path dataFolder, Logger logger) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile.toFile());

        if (logger != null) {
            for (String key : yaml.getKeys(true)) {
                // Dynamic sections (per-game pool overrides) aren't in KNOWN_KEYS.
                if (key.startsWith("arena-pool.games.")) continue;
                if (!KNOWN_KEYS.contains(key)) {
                    logger.warning("Unknown config key '" + key + "' in "
                            + configFile.getFileName() + " - check for a typo.");
                }
            }
        }

        CoreConfig.NetworkMode mode = parseEnum(
                yaml.getString("network.mode", "standalone"),
                CoreConfig.NetworkMode.class,
                CoreConfig.NetworkMode.STANDALONE);
        String rawServerId = yaml.getString("network.server-id", "auto");
        String serverId = resolveServerId(rawServerId);
        String lobbyServer = yaml.getString("network.lobby-server", "lobby");
        CoreConfig.NetworkConfig network = new CoreConfig.NetworkConfig(mode, serverId, lobbyServer);

        ConfigurationSection redisSection = section(yaml, "redis");
        CoreConfig.RedisConfig redis = new CoreConfig.RedisConfig(
                redisSection.getString("host", "localhost"),
                redisSection.getInt("port", 6379),
                redisSection.getString("password", ""));

        ConfigurationSection dbSection = section(yaml, "database");
        CoreConfig.DatabaseType dbType = parseEnum(
                dbSection.getString("type", "sqlite"),
                CoreConfig.DatabaseType.class,
                CoreConfig.DatabaseType.SQLITE);

        Path sqliteFile = resolvePath(dataFolder, dbSection.getString("sqlite-file", "plugins/TeaCore/tea.db"));

        ConfigurationSection mysqlSection = section(dbSection, "mysql");
        CoreConfig.DatabaseConfig database = new CoreConfig.DatabaseConfig(
                dbType,
                sqliteFile,
                mysqlSection.getString("host", "localhost"),
                mysqlSection.getInt("port", 3306),
                mysqlSection.getString("database", "tea"),
                mysqlSection.getString("user", "tea"),
                mysqlSection.getString("password", ""));

        ConfigurationSection i18nSection = section(yaml, "i18n");
        String defaultLocale = i18nSection.getString("default-locale", "en_US");
        List<String> fallback = i18nSection.getStringList("fallback-chain");
        if (fallback.isEmpty())
            fallback = List.of(defaultLocale);
        CoreConfig.I18nConfig i18n = new CoreConfig.I18nConfig(defaultLocale, List.copyOf(fallback));

        ConfigurationSection dashboardSection = section(yaml, "dashboard");
        boolean dashEnabled = dashboardSection.getBoolean("enabled", false);
        String bindAddress = dashboardSection.getString("bind-address", "0.0.0.0");
        int dashPort = dashboardSection.getInt("port", 8080);
        String adminToken = dashboardSection.getString("admin-token", "");
        if (adminToken == null || adminToken.isBlank()) {
            adminToken = generateToken();
            yaml.set("dashboard.admin-token", adminToken);
            try {
                yaml.save(configFile.toFile());
                if (logger != null) {
                    logger.info("Generated dashboard admin token and wrote it to "
                            + configFile.getFileName() + ". Keep it secret.");
                }
            } catch (IOException e) {
                if (logger != null) {
                    logger.log(Level.WARNING,
                            "Could not persist generated dashboard admin token to "
                                    + configFile + "; token will regenerate on next boot.",
                            e);
                }
            }
        }
        CoreConfig.DashboardConfig dashboard = new CoreConfig.DashboardConfig(
                dashEnabled, bindAddress, dashPort, adminToken);

        CoreConfig.ArenaPoolMode poolMode = parseEnum(
                yaml.getString("arena-pool.mode", "standalone"),
                CoreConfig.ArenaPoolMode.class,
                CoreConfig.ArenaPoolMode.STANDALONE);
        java.util.Map<String, CoreConfig.GamePoolOverride> gamePools = new java.util.HashMap<>();
        ConfigurationSection gamesSection = yaml.getConfigurationSection("arena-pool.games");
        if (gamesSection != null) {
            for (String gameId : gamesSection.getKeys(false)) {
                ConfigurationSection g = gamesSection.getConfigurationSection(gameId);
                if (g == null) continue;
                gamePools.put(gameId, new CoreConfig.GamePoolOverride(
                        g.getInt("min-available", -1),
                        g.getInt("max-total", -1),
                        g.getInt("preload-on-startup", -1)));
            }
        }
        CoreConfig.ArenaPoolConfig arenaPool = new CoreConfig.ArenaPoolConfig(poolMode, gamePools);

        return new CoreConfig(network, redis, database, i18n, dashboard, arenaPool);
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Back-compat overload used by older call sites. Prefer the Logger-aware
     * variant.
     */
    public static CoreConfig load(Path configFile, Path dataFolder) {
        return load(configFile, dataFolder, null);
    }

    private static String resolveServerId(String raw) {
        if (raw != null && !raw.isBlank() && !"auto".equalsIgnoreCase(raw.trim())) {
            return raw.trim();
        }
        String env = System.getenv("TEA_SERVER_ID");
        if (env != null && !env.isBlank())
            return env.trim();
        return "srv-" + Long.toHexString(System.nanoTime());
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection s = parent.getConfigurationSection(path);
        return s != null ? s : parent.createSection(path);
    }

    private static Path resolvePath(Path dataFolder, String raw) {
        Path p = Path.of(raw);
        if (p.isAbsolute())
            return p;
        if (raw.startsWith("plugins/"))
            return Path.of(raw);
        return dataFolder.resolve(raw);
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (value == null)
            return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
