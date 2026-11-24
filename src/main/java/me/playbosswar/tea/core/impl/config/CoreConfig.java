package me.playbosswar.tea.core.impl.config;

import java.nio.file.Path;
import java.util.List;

public record CoreConfig(
                NetworkConfig network,
                RedisConfig redis,
                DatabaseConfig database,
                I18nConfig i18n,
                DashboardConfig dashboard,
                ArenaPoolConfig arenaPool) {
        public enum NetworkMode {
                STANDALONE, BUNGEECORD, MULTI_SERVER
        }

        public enum DatabaseType {
                SQLITE, MYSQL
        }

        public record NetworkConfig(NetworkMode mode, String serverId, String lobbyServer,
                        boolean eligibleMatchmaker) {
        }

        public record RedisConfig(String host, int port, String password) {
        }

        public record DatabaseConfig(
                        DatabaseType type,
                        Path sqliteFile,
                        String mysqlHost,
                        int mysqlPort,
                        String mysqlDatabase,
                        String mysqlUser,
                        String mysqlPassword) {
        }

        public record I18nConfig(String defaultLocale, List<String> fallbackChain) {
        }

        /**
         * @param adminToken empty when turned off with {@code admin-token: off}.
         * @param publicUrl  empty if unknown.
         */
        public record DashboardConfig(boolean enabled, String bindAddress, int port, String adminToken,
                                      String publicUrl) {
        }

        /**
         * STANDALONE: counts are per server. CENTRAL: only the matchmaker leader runs
         * the pool manager and counts are network-wide. EXTERNAL: one arena per server,
         * scaled by an external orchestrator; the pool manager does nothing.
         */
        public enum ArenaPoolMode {
                STANDALONE, CENTRAL, EXTERNAL
        }

        /** Takes precedence over the game's own {@link me.playbosswar.tea.core.api.game.PoolConfig}. */
        public record GamePoolOverride(int minAvailable, int maxTotal, int preloadOnStartup) {
        }

        public record ArenaPoolConfig(ArenaPoolMode mode, java.util.Map<String, GamePoolOverride> games) {
                public ArenaPoolConfig {
                        games = games == null ? java.util.Map.of() : java.util.Map.copyOf(games);
                }
        }

        public NetworkMode networkMode() {
                return network.mode();
        }
}
