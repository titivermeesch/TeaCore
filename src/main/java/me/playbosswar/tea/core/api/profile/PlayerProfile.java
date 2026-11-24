package me.playbosswar.tea.core.api.profile;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Engine-wide player data shared by all Tea games. Game-specific stats live in
 * each game's own schema.
 */
public interface PlayerProfile {

    UUID uuid();

    /** First join on any Tea server. */
    Instant firstSeen();

    Instant lastSeen();

    /** Last-known client locale, or {@code null} if the player never reported one. */
    @Nullable
    String locale();
}
