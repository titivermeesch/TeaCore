package me.playbosswar.tea.core.api.stats;

import java.util.Collection;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Registry of {@link StatsProvider}s keyed by {@link GameId}. Games register
 * once in {@code onEnable}.
 */
public interface StatsRegistry {

    /** Replaces any provider already registered for the same game. */
    void register(StatsProvider provider);

    /** No-op if nothing is registered for the game. */
    void unregister(GameId gameId);

    Optional<StatsProvider> forGame(GameId gameId);

    /** @return a snapshot */
    Collection<StatsProvider> all();

    /**
     * Persists a schema in the shared database so other servers (notably the
     * lobby) can render this game's stats without its provider class. Call in
     * {@code onEnable} alongside {@link #register(StatsProvider)}. Replaces any
     * earlier schema for the same game in full.
     */
    void registerSchema(StatsSchema schema);

    /**
     * Every persisted schema from the shared database, including those
     * registered on other servers.
     */
    Collection<StatsSchema> allSchemas();

    /**
     * Returns the locally registered provider for the schema's game if there is
     * one, otherwise a generic database-backed provider built from the schema.
     */
    StatsProvider providerFor(StatsSchema schema);
}
