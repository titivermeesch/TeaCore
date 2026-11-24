package me.playbosswar.tea.core.api.game;

import java.util.Collection;
import java.util.Optional;

/**
 * Games known to this server. Register in {@code onEnable} and unregister in
 * {@code onDisable}.
 */
public interface GameRegistry {

    /** Replaces any game already registered with the same id. */
    void register(GameDefinition definition);

    /** No-op if the game is not registered. */
    void unregister(GameId id);

    Optional<GameDefinition> get(GameId id);

    /** @return a snapshot */
    Collection<GameDefinition> all();
}
