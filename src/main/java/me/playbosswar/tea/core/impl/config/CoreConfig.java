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

        /**
         * @param mode               coordination mode for the fleet.
         * @param serverId           this backend's id, used by the proxy and the
         *                           coordination backend.
         * @param lobbyServer        proxy-side name of the lobby (where players are
         *                           sent at match end).
         * @param eligibleMatchmaker whether this backend is allowed to acquire the
         *                           matchmaker leader lock and run the cluster-wide
         *                           pool manager. Default {@code true}, which is
         *                           correct for standalone deployments. In central
         *                           mode, set to {@code true} on the lobby and
         *                           {@code false} on every arena backend so the
         *                           lobby's {@code arena-pool.games} config is the
         *                           single source of truth for pool sizing.
         */
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

        public record DashboardConfig(boolean enabled, String bindAddress, int port, String adminToken) {
        }

        /**
         * Arena pool sizing mode.
         *
         * <ul>
         * <li>{@link #STANDALONE} - this backend hosts arenas itself; pool manager runs
         * locally and counts are per-backend.</li>
         * <li>{@link #CENTRAL} - lobby + multiple arena backends. Only the matchmaker
         * leader runs the pool manager; counts are network-wide and allocations fan out
         * across backends via control commands.</li>
         * <li>{@link #EXTERNAL} - one arena per backend (k8s pod per match). External
         * orchestrator handles scaling; core's pool manager is a no-op.</li>
         * </ul>
         */
        public enum ArenaPoolMode {
                STANDALONE, CENTRAL, EXTERNAL
        }

        /**
         * Admin override for a single game's pool sizing. Takes precedence over the
         * {@link me.playbosswar.tea.core.api.game.PoolConfig} the game ships with in its
         * {@link me.playbosswar.tea.core.api.game.GameDefinition}.
         */
        public record GamePoolOverride(int minAvailable, int maxTotal, int preloadOnStartup) {
        }

        public record ArenaPoolConfig(ArenaPoolMode mode, java.util.Map<String, GamePoolOverride> games) {
                public ArenaPoolConfig {
                        games = games == null ? java.util.Map.of() : java.util.Map.copyOf(games);
                }
        }

        /** Legacy accessor for call sites that haven't migrated yet. */
        public NetworkMode networkMode() {
                return network.mode();
        }
}
