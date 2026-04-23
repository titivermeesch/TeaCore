package me.playbosswar.tea.core.impl.stats;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.stats.StatsProvider;
import me.playbosswar.tea.core.api.stats.StatsRegistry;

public final class StatsRegistryImpl implements StatsRegistry {

    private final ConcurrentHashMap<GameId, StatsProvider> providers = new ConcurrentHashMap<>();

    @Override
    public void register(StatsProvider provider) {
        providers.put(provider.gameId(), provider);
    }

    @Override
    public void unregister(GameId gameId) {
        providers.remove(gameId);
    }

    @Override
    public Optional<StatsProvider> forGame(GameId gameId) {
        return Optional.ofNullable(providers.get(gameId));
    }

    @Override
    public Collection<StatsProvider> all() {
        return Collections.unmodifiableCollection(providers.values());
    }
}
