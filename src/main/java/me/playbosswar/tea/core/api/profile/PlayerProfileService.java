package me.playbosswar.tea.core.api.profile;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads and caches {@link PlayerProfile}s from the shared database.
 */
public interface PlayerProfileService {

    /**
     * Returns the profile, inserting a row if none exists. A cache miss hits the
     * database and may block briefly; cached lookups are safe on the main thread.
     */
    PlayerProfile getOrCreate(UUID uuid);

    /** Cache only; never touches the database. */
    Optional<PlayerProfile> get(UUID uuid);
}
