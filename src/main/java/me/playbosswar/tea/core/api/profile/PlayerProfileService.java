package me.playbosswar.tea.core.api.profile;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads and caches {@link PlayerProfile}s, backed by the shared database.
 */
public interface PlayerProfileService {

    /**
     * Returns the profile for a player, creating one on first access.
     *
     * <p>
     * Hits the database if the profile is not already cached, and inserts a new row
     * if
     * none exists yet. Safe to call on the main thread for cached lookups; the
     * initial
     * fetch may block briefly.
     *
     * @param uuid player uuid; must not be {@code null}.
     * @return the profile, never {@code null}.
     */
    PlayerProfile getOrCreate(UUID uuid);

    /**
     * Returns a cached profile without touching the database.
     *
     * @param uuid player uuid; must not be {@code null}.
     * @return the profile, or empty if none is currently cached.
     */
    Optional<PlayerProfile> get(UUID uuid);
}
