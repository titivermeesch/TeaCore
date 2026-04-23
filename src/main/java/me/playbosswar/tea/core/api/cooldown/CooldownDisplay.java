package me.playbosswar.tea.core.api.cooldown;

/**
 * How a cooldown's remaining time should be surfaced to the player.
 *
 * <p>Picked per-cooldown when starting one via
 * {@link CooldownService#start(org.bukkit.entity.Player, String, java.time.Duration, CooldownDisplay)}.
 */
public enum CooldownDisplay {
    /** Overlays remaining progress on the player's experience bar. */
    XP_BAR,
    /** Renders a per-tick countdown in the action bar. */
    ACTION_BAR,
    /** Renders a per-tick countdown in a boss bar. */
    BOSS_BAR,
    /** Tracks the cooldown silently without any visible surface. */
    NONE
}
