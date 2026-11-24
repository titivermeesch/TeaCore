package me.playbosswar.tea.core.api.arena;

/**
 * The states an {@link Arena} moves through, in order. Each change fires an
 * {@link ArenaStateChangeEvent}.
 */
public enum ArenaState {
    /** World being created and map pasted. */
    ALLOCATING,
    /** Accepting players until min-players is reached. */
    WAITING,
    /** Enough players; counting down to start. */
    COUNTDOWN,
    /** Short freeze after the countdown while players are teleported and kits applied. */
    STARTING,
    LIVE,
    /** Winner determined; brief freeze before cleanup. */
    ENDING,
    /** World being rolled back or torn down. */
    RESETTING,
    /** Final state; the arena is not reused. */
    RELEASED
}
