package me.playbosswar.tea.core.api.arena;

/**
 * Lifecycle states an {@link Arena} moves through from allocation to release.
 *
 * <p>
 * Transitions are driven by the arena lifecycle ticker and emit an
 * {@link ArenaStateChangeEvent} on each change.
 */
public enum ArenaState {
    /** World being created and map pasted. */
    ALLOCATING,
    /** Map ready, accepting players, waiting for min-players. */
    WAITING,
    /** Enough players present; counting down to start. */
    COUNTDOWN,
    /**
     * Freeze frame between countdown end and gameplay start (teleport, apply kits).
     */
    STARTING,
    /** Gameplay in progress. */
    LIVE,
    /** Winner determined; brief freeze before cleanup. */
    ENDING,
    /** World being rolled back or torn down. */
    RESETTING,
    /** Arena slot freed; can be reallocated or disposed. */
    RELEASED
}
