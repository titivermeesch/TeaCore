package me.playbosswar.tea.core.api.effect;

import java.util.Optional;

/**
 * Registry of named {@link EffectBundle}s shared across all games on the server.
 *
 * <p>Games register their bundles in {@code onEnable} and look them up by id at playback
 * time. Ids should be namespaced per game (e.g. {@code "ascend:evolve"}) to avoid
 * collisions.
 */
public interface EffectService {

    /**
     * Registers a bundle under the given id.
     *
     * <p>Overwrites any existing registration for the same id.
     *
     * @param id     unique bundle identifier; must not be {@code null}.
     * @param bundle bundle to register; must not be {@code null}.
     */
    void register(String id, EffectBundle bundle);

    /**
     * Returns a previously-registered bundle by id.
     *
     * @param id bundle identifier to look up; must not be {@code null}.
     * @return the bundle, or empty if no bundle is registered under that id.
     */
    Optional<EffectBundle> get(String id);
}
