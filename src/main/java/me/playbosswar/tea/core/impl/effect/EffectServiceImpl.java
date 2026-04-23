package me.playbosswar.tea.core.impl.effect;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.effect.EffectBundle;
import me.playbosswar.tea.core.api.effect.EffectService;

public final class EffectServiceImpl implements EffectService {

    private final ConcurrentHashMap<String, EffectBundle> bundles = new ConcurrentHashMap<>();

    @Override
    public void register(String id, EffectBundle bundle) {
        bundles.put(id, bundle);
    }

    @Override
    public Optional<EffectBundle> get(String id) {
        return Optional.ofNullable(bundles.get(id));
    }
}
