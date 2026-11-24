package me.playbosswar.tea.core.api.effect;

import java.util.Optional;

/**
 * Server-wide registry of named {@link EffectBundle}s, shared by all games. Namespace
 * ids per game (e.g. {@code "ascend:evolve"}) to avoid collisions.
 */
public interface EffectService {

    /** Replaces any bundle already registered under {@code id}. */
    void register(String id, EffectBundle bundle);

    Optional<EffectBundle> get(String id);
}
