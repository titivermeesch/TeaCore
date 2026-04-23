package me.playbosswar.tea.core.api.cooldown;

import java.time.Duration;
import org.bukkit.entity.Player;

/**
 * Tracks per-player, per-id cooldown timers and optionally surfaces their remaining time.
 *
 * <p>Typical uses include ability cooldowns, item-use cooldowns, and anti-spam timers. All
 * active cooldowns for a player can be cleared at once via {@link #cancelAll(Player)}.
 */
public interface CooldownService {

    /**
     * Starts a cooldown for a player, using the id as its display label.
     *
     * <p>Equivalent to
     * {@link #start(Player, String, String, Duration, CooldownDisplay) start(player, id, id, duration, display)}.
     * Schedules UI updates on the main thread according to {@code display}.
     *
     * @param player   player to cool down; must not be {@code null}.
     * @param id       tracking key for the cooldown; must not be {@code null}.
     * @param duration total cooldown duration; must be positive.
     * @param display  how to surface the remaining time; must not be {@code null}.
     */
    default void start(Player player, String id, Duration duration, CooldownDisplay display) {
        start(player, id, id, duration, display);
    }

    /**
     * Starts a cooldown with a caller-supplied display label.
     *
     * <p>The {@code id} is used as the tracking key (checked by {@link #isActive} and
     * {@link #remaining}); the {@code label} is cosmetic and only shown when
     * {@code display == ACTION_BAR}. Schedules UI updates on the main thread according to
     * {@code display} and replaces any existing cooldown for the same id.
     *
     * @param player   player to cool down; must not be {@code null}.
     * @param id       tracking key for the cooldown; must not be {@code null}.
     * @param label    human-readable label shown in the action bar; must not be {@code null}.
     * @param duration total cooldown duration; must be positive.
     * @param display  how to surface the remaining time; must not be {@code null}.
     */
    void start(Player player, String id, String label, Duration duration, CooldownDisplay display);

    /**
     * Checks whether a cooldown is currently running.
     *
     * @param player player to inspect; must not be {@code null}.
     * @param id     cooldown id to check; must not be {@code null}.
     * @return {@code true} if a cooldown with that id is active for the player.
     */
    boolean isActive(Player player, String id);

    /**
     * Returns the time left on a cooldown.
     *
     * @param player player to inspect; must not be {@code null}.
     * @param id     cooldown id to query; must not be {@code null}.
     * @return the remaining duration, or {@link Duration#ZERO} if none is active.
     */
    Duration remaining(Player player, String id);

    /**
     * Cancels every active cooldown for a player.
     *
     * <p>Intended for lifecycle moments where all state must reset at once, such as death,
     * match end, or leaving an arena. Removes any UI surfaces tied to those cooldowns.
     *
     * @param player player whose cooldowns to clear; must not be {@code null}.
     */
    void cancelAll(Player player);
}
