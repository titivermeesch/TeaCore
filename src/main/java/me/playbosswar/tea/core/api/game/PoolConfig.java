package me.playbosswar.tea.core.api.game;

/**
 * Warm-pool configuration for a game's arenas on a single server.
 *
 * <p>The pool keeps at least {@code minAvailable} idle arenas in {@code WAITING} so
 * players can drop straight into a game, up to {@code maxTotal} concurrent arenas.
 * Scaling decisions are throttled by {@code scaleCooldownSeconds}.
 *
 * @param minAvailable         minimum number of idle, joinable arenas maintained on the
 *                             server.
 * @param maxTotal             hard cap on concurrent arenas for this game.
 * @param preloadOnStartup     how many arenas to allocate eagerly in {@code onEnable}.
 * @param scaleCooldownSeconds minimum seconds between consecutive scale-up decisions.
 */
public record PoolConfig(
        int minAvailable,
        int maxTotal,
        int preloadOnStartup,
        int scaleCooldownSeconds) {

    /**
     * Returns a conservative default configuration.
     *
     * <p>Values: {@code minAvailable=1}, {@code maxTotal=4}, {@code preloadOnStartup=1},
     * {@code scaleCooldownSeconds=10}.
     *
     * @return a default pool configuration, never {@code null}.
     */
    public static PoolConfig defaults() {
        return new PoolConfig(1, 4, 1, 10);
    }
}
