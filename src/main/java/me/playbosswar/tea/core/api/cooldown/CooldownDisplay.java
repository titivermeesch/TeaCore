package me.playbosswar.tea.core.api.cooldown;

/**
 * How a cooldown started with
 * {@link CooldownService#start(org.bukkit.entity.Player, String, java.time.Duration, CooldownDisplay)}
 * is shown to the player.
 */
public enum CooldownDisplay {
    /** Remaining progress on the experience bar. */
    XP_BAR,
    /** Countdown in the action bar. */
    ACTION_BAR,
    /**
     * A 10-cell action bar progress bar with the label and remaining seconds:
     * {@code Ground Pound  ████████░░  0.4s}.
     */
    PROGRESS_BAR,
    /** Countdown in a boss bar. */
    BOSS_BAR,
    /** Tracked but not shown. */
    NONE
}
