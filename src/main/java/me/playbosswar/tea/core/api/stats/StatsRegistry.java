package me.playbosswar.tea.core.api.stats;

import java.util.Collection;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Registry of {@link StatsProvider}s keyed by {@link GameId}.
 *
 * <p>
 * Games register their provider once in {@code onEnable}; {@code /tea stats}
 * iterates
 * the registered providers when printing totals.
 */
public interface StatsRegistry {

    /**
     * Registers a stats provider.
     *
     * <p>
     * Overwrites any previous registration for the same game id.
     *
     * @param provider provider to register; must not be {@code null}.
     */
    void register(StatsProvider provider);

    /**
     * Unregisters the provider for a game.
     *
     * <p>
     * No-op if no provider is registered for the game.
     *
     * @param gameId id of the game whose provider to remove; must not be
     *               {@code null}.
     */
    void unregister(GameId gameId);

    /**
     * Looks up the provider registered for a game.
     *
     * @param gameId id of the game; must not be {@code null}.
     * @return the provider, or empty if no provider is registered.
     */
    Optional<StatsProvider> forGame(GameId gameId);

    /**
     * Returns every currently-registered provider.
     *
     * @return a snapshot collection, never {@code null}.
     */
    Collection<StatsProvider> all();
}
