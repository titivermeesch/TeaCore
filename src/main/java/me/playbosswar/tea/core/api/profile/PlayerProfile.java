package me.playbosswar.tea.core.api.profile;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent profile for a player, shared across all Tea minigames on the
 * server.
 *
 * <p>
 * Profiles are loaded and created on demand via {@link PlayerProfileService}
 * and hold
 * only engine-wide fields. Game-specific stats live in each game's own schema.
 */
public interface PlayerProfile {

    /**
     * Returns the player's Minecraft uuid.
     *
     * @return the uuid, never {@code null}.
     */
    UUID uuid();

    /**
     * Returns the timestamp of this player's first join on any Tea server.
     *
     * @return the instant, never {@code null}.
     */
    Instant firstSeen();

    /**
     * Returns the timestamp of this player's most recent join.
     *
     * @return the instant, never {@code null}.
     */
    Instant lastSeen();

    /**
     * Returns the player's last-known client locale.
     *
     * @return the locale code (e.g. {@code "en_US"}), or {@code null} if the player
     *         has
     *         never logged in and therefore never reported a locale.
     */
    @Nullable
    String locale();
}
