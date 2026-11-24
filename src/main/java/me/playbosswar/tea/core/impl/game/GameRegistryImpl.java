package me.playbosswar.tea.core.impl.game;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.GameRegistry;

public final class GameRegistryImpl implements GameRegistry {

    private final ConcurrentHashMap<GameId, GameDefinition> games = new ConcurrentHashMap<>();
    private final Consumer<GameDefinition> onRegister;
    private final Consumer<GameDefinition> onUnregister;

    public GameRegistryImpl(Consumer<GameDefinition> onRegister, Consumer<GameDefinition> onUnregister) {
        this.onRegister = onRegister;
        this.onUnregister = onUnregister;
    }

    @Override
    public void register(GameDefinition definition) {
        GameDefinition previous = games.put(definition.id(), definition);
        // Registering again (after a settings change) only swaps the definition:
        // the pool was preloaded and the game published the first time.
        if (previous == null) {
            onRegister.accept(definition);
        }
    }

    @Override
    public void unregister(GameId id) {
        GameDefinition removed = games.remove(id);
        if (removed != null)
            onUnregister.accept(removed);
    }

    @Override
    public Optional<GameDefinition> get(GameId id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public Collection<GameDefinition> all() {
        return Collections.unmodifiableCollection(games.values());
    }
}
