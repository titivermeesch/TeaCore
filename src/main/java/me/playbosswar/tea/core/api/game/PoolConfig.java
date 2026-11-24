package me.playbosswar.tea.core.api.game;

/**
 * Warm pool of a game's arenas on one server.
 *
 * @param minAvailable         idle {@code WAITING} arenas kept ready to join.
 * @param maxTotal             cap on concurrent arenas for this game.
 * @param preloadOnStartup     arenas allocated eagerly in {@code onEnable}.
 * @param scaleCooldownSeconds minimum seconds between scale-ups.
 */
public record PoolConfig(
        int minAvailable,
        int maxTotal,
        int preloadOnStartup,
        int scaleCooldownSeconds) {

    public static PoolConfig defaults() {
        return new PoolConfig(1, 4, 1, 10);
    }
}
