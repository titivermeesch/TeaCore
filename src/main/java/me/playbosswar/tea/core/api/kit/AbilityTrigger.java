package me.playbosswar.tea.core.api.kit;

/**
 * Input events that can fire an {@link Ability} via an {@link AbilityBinding}.
 *
 * <p>
 * Click triggers match only when the ability's binding item is held; non-click
 * triggers
 * fire regardless of the held item.
 */
public enum AbilityTrigger {
    /** Player right-clicked while holding the ability's bound item. */
    RIGHT_CLICK_ITEM,
    /** Player left-clicked while holding the ability's bound item. */
    LEFT_CLICK_ITEM,
    /** Player began crouching. */
    SHIFT_PRESS,
    /** Player stopped crouching. */
    SHIFT_RELEASE,
    /** Player jumped (left the ground by jumping). */
    JUMP,
    /** Player took damage. */
    DAMAGE_TAKEN,
    /** Player killed another entity. */
    KILL,
    /** Ability is passive and fires on a continuous tick or on kit application. */
    PASSIVE
}
