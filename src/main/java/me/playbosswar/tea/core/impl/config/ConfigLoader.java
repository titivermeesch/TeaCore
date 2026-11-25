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

    // Unknown keys log a startup warning so typos don't silently fall back to defaults.
    // Keep in sync with the default config.yml and CoreConfig.
    private static final Set<String> KNOWN_KEYS = Set.of(
            "network",
            "network.mode",
            "network.server-id",
            "network.lobby-server",
            "network.eligible-matchmaker",
            "network.secret",
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
            "dashboard.public-url",
            "arena-pool",
            "arena-pool.mode",
            "arena-pool.spare-worlds",
            "arena-pool.scale-down-idle-seconds",
            "arena-pool.games",
            "map-setup-scoreboard",
            "map-setup-scoreboard.enabled",
            "lobby",
            "lobby.enabled",
            "lobby.pvp",
            "lobby.manage-spawn",
            "lobby.gamemode",
            "lobby.spawn",
            "lobby.spawn.world",
            "lobby.spawn.x",
            "lobby.spawn.y",
            "lobby.spawn.z",
            "lobby.spawn.yaw",
            "lobby.spawn.pitch",
            "lobby.hotbar",
            "lobby.hotbar.items",
            "features",
            "features.sidebar",
            "features.lobby-sidebar",
            "features.menus",
            "features.placeholders",
            "features.queue-bossbar",
            "features.lobby-hotbar",
            "features.match-history",
            "features.match-recap",
            "features.map-editor-tools",
            "features.spectator-tools",
            "features.map-voting",
            "features.waiting-protection",
            "features.kit-item",
            "features.leave-item",
            "features.ranked",
            "features.quests",
            "parties",
            "parties.enabled",
            "parties.leader-only",
            "ranked",
            "ranked.start-rating",
            "ranked.k-factor",
            "ranked.range",
            "ranked.widen-per-10s",
            "ranked.max-range",
            "map-voting",
            "map-voting.options",
            "map-voting.hotbar-slot",
            "kit-item",
            "kit-item.material",
            "kit-item.hotbar-slot",
            "leave-item",
            "waiting-area",
            "cloudnet",
            "leave-item.material",
            "leave-item.hotbar-slot",
            "npcs",
            "features.games",
            "combat",
            "combat.kill-credit-window-ms",
            "combat.assist-window-ms",
            "match-history",
            "match-history.keep-days",
            "maps",
            "maps.keep-versions",
            "maps.storage",
            "share-config-files",
            "network.group",
            "hosting",
            "hosting.games",
            "hosting.map",
            "hosting.maps",
            "metrics",
            "metrics.token",
            "discord",
            "discord.match-results", "discord.match-results.url",
            "discord.staff-actions", "discord.staff-actions.url",
            "discord.achievements", "discord.achievements.url",
            "discord.server-status", "discord.server-status.url",
            "leaderboards",
            "leaderboards.refresh-seconds",
            "game-display");

    private ConfigLoader() {
    }

    public static CoreConfig load(Path configFile, Path dataFolder, Logger logger) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile.toFile());

        if (logger != null) {
            for (String key : yaml.getKeys(true)) {
                // Dynamic sections (per-game pool overrides) aren't in KNOWN_KEYS.
                if (key.startsWith("arena-pool.games.") || key.startsWith("features.games.")
                        || key.startsWith("game-display.") || key.startsWith("npcs.")
                        || key.startsWith("waiting-area.") || key.startsWith("cloudnet.")
                        || key.startsWith("hosting.maps."))
                    continue;
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
        // Defaults to true so a single server is its own matchmaker. Central deployments set
        // it false on arena servers so the lobby's arena-pool.games config sizes the fleet.
        boolean eligibleMatchmaker = yaml.getBoolean("network.eligible-matchmaker", true);
        CoreConfig.NetworkConfig network = new CoreConfig.NetworkConfig(mode, serverId, lobbyServer,
                eligibleMatchmaker);

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
        String bindAddress = dashboardSection.getString("bind-address", "127.0.0.1");
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
        if ("off".equalsIgnoreCase(adminToken.trim())) {
            adminToken = "";
        }
        String publicUrl = dashboardSection.getString("public-url", "").trim();
        while (publicUrl.endsWith("/")) {
            publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
        }
        CoreConfig.DashboardConfig dashboard = new CoreConfig.DashboardConfig(
                dashEnabled, bindAddress, dashPort, adminToken, publicUrl);

        CoreConfig.ArenaPoolMode poolMode = parseEnum(
                yaml.getString("arena-pool.mode", "standalone"),
                CoreConfig.ArenaPoolMode.class,
                CoreConfig.ArenaPoolMode.STANDALONE);
        java.util.Map<String, CoreConfig.GamePoolOverride> gamePools = new java.util.HashMap<>();
        ConfigurationSection gamesSection = yaml.getConfigurationSection("arena-pool.games");
        if (gamesSection != null) {
            for (String gameId : gamesSection.getKeys(false)) {
                ConfigurationSection g = gamesSection.getConfigurationSection(gameId);
                if (g == null)
                    continue;
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

    public static CoreConfig load(Path configFile, Path dataFolder) {
        return load(configFile, dataFolder, null);
    }

    private static String resolveServerId(String raw) {
        if (raw != null && !raw.isBlank() && !raw.trim().equalsIgnoreCase("auto")) {
            return raw.trim();
        }
        String env = System.getenv("TEA_SERVER_ID");
        if (env != null && !env.isBlank())
            return env.trim();
        String cloud = me.playbosswar.tea.core.impl.cloud.CloudPlatform.detectServiceName();
        if (cloud != null)
            return cloud;
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
