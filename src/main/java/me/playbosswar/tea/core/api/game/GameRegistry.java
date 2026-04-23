package me.playbosswar.tea.core.api.game;

import java.util.Collection;
import java.util.Optional;

/**
 * Central registry of {@link GameDefinition}s known to this server.
 *
 * <p>Game plugins call {@link #register(GameDefinition)} in {@code onEnable} and
 * {@link #unregister(GameId)} in {@code onDisable}. Subsystems such as the arena manager
 * and matchmaker consult the registry at runtime.
 */
public interface GameRegistry {

    /**
     * Registers a game type.
     *
     * <p>Overwrites any previous registration for the same id. Games typically call this
     * in their {@code onEnable}.
     *
     * @param definition definition to register; must not be {@code null}.
     */
    void register(GameDefinition definition);

    /**
     * Unregisters a game type.
     *
     * <p>No-op if no game is registered under that id. Called by games in {@code onDisable}.
     *
     * @param id id to remove; must not be {@code null}.
     */
    void unregister(GameId id);

    /**
     * Returns the definition for a game id, if registered.
     *
     * @param id id to look up; must not be {@code null}.
     * @return the definition, or empty if not registered.
     */
    Optional<GameDefinition> get(GameId id);

    /**
     * Returns every currently-registered game definition.
     *
     * @return a snapshot collection, never {@code null}.
     */
    Collection<GameDefinition> all();
}
