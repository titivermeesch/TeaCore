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

    /**
     * Persists a stats schema in the shared database so other servers in the
     * fleet (notably the lobby) can render this game's stats without having
     * the game's own provider class on their classpath.
     *
     * <p>Game plugins call this once in {@code onEnable}, alongside
     * {@link #register(StatsProvider)}. Re-registering with the same
     * {@link GameId} replaces the previous schema in full.
     *
     * @param schema schema to persist; must not be {@code null}.
     */
    void registerSchema(StatsSchema schema);

    /**
     * Returns every persisted schema, read from the shared database. Includes
     * schemas registered on other servers, so a lobby sees every game in the
     * fleet that has ever called {@link #registerSchema(StatsSchema)}.
     *
     * @return schema snapshot; never {@code null}.
     */
    Collection<StatsSchema> allSchemas();

    /**
     * Resolves the effective provider for a schema. Returns the in-memory
     * provider registered via {@link #register(StatsProvider)} when one exists
     * (so games keep using their custom logic on their own servers), and falls
     * back to a generic database-backed provider built from the schema
     * otherwise (so a lobby with no game plugins can still render the GUI).
     *
     * @param schema schema to resolve; must not be {@code null}.
     * @return a provider that can {@link StatsProvider#fetch(java.util.UUID) fetch} stats
     *         for the schema's game; never {@code null}.
     */
    StatsProvider providerFor(StatsSchema schema);
}
