package me.playbosswar.tea.core.api.kit;

/**
 * Input that fires an {@link Ability} through an {@link AbilityBinding}. Click
 * triggers require the ability's bound item to be held; the others fire
 * regardless of the held item.
 */
public enum AbilityTrigger {
    RIGHT_CLICK_ITEM,
    LEFT_CLICK_ITEM,
    /** Player began sneaking. */
    SHIFT_PRESS,
    /** Player stopped sneaking. */
    SHIFT_RELEASE,
    JUMP,
    DAMAGE_TAKEN,
    /** Player killed another entity. */
    KILL,
    /** Fires on a continuous tick or when the kit is applied. */
    PASSIVE
}
