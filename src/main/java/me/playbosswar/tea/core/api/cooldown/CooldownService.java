package me.playbosswar.tea.core.api.cooldown;

import java.time.Duration;
import org.bukkit.entity.Player;

/** Per-player cooldown timers, keyed by id, with optional on-screen display. */
public interface CooldownService {

    /**
     * Same as {@link #start(Player, String, String, Duration, CooldownDisplay)}
     * with the id as the label.
     */
    default void start(Player player, String id, Duration duration, CooldownDisplay display) {
        start(player, id, id, duration, display);
    }

    /**
     * Replaces any running cooldown with the same id.
     *
     * @param label    shown only with {@link CooldownDisplay#ACTION_BAR}
     * @param duration must be positive
     */
    void start(Player player, String id, String label, Duration duration, CooldownDisplay display);

    boolean isActive(Player player, String id);

    /** @return {@link Duration#ZERO} if no cooldown is running */
    Duration remaining(Player player, String id);

    /** Also removes their on-screen displays. */
    void cancelAll(Player player);
}
