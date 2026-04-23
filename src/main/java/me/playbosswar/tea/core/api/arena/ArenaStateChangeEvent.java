package me.playbosswar.tea.core.api.arena;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired once per {@link ArenaState} transition.
 *
 * <p>Emitted AFTER the arena has been moved into the new state, so listeners observing a
 * transition into {@link ArenaState#LIVE} can safely inspect live match state. Not
 * cancellable.
 */
public final class ArenaStateChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final ArenaState from;
    private final ArenaState to;

    /**
     * Constructs a new state-change event.
     *
     * @param arena arena whose state changed; must not be {@code null}.
     * @param from  previous state; must not be {@code null}.
     * @param to    new state; must not be {@code null}.
     */
    public ArenaStateChangeEvent(Arena arena, ArenaState from, ArenaState to) {
        this.arena = arena;
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the arena whose state changed.
     *
     * @return the arena, never {@code null}.
     */
    public Arena arena() {
        return arena;
    }

    /**
     * Returns the state the arena was in before the transition.
     *
     * @return the previous state, never {@code null}.
     */
    public ArenaState from() {
        return from;
    }

    /**
     * Returns the state the arena is now in.
     *
     * @return the new state, never {@code null}.
     */
    public ArenaState to() {
        return to;
    }

    /**
     * {@inheritDoc}
     *
     * @return the handler list for this event.
     */
    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the static handler list Bukkit uses to dispatch this event.
     *
     * @return the shared handler list, never {@code null}.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
