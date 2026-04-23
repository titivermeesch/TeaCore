package me.playbosswar.tea.core.api.arena;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired once when an arena's {@link ArenaState#LIVE} phase reaches the configured
 * {@code maxMatchSeconds} cap.
 *
 * <p>The owning game is expected to listen, determine a winner (or draw), and call
 * {@link ArenaManager#endMatch(ArenaId, java.util.UUID)}. If no handler responds,
 * the arena stays in {@link ArenaState#LIVE}. Fired exactly once per match. Not cancellable.
 */
public final class MatchTimedOutEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;

    /**
     * Constructs a new match-timed-out event.
     *
     * @param arena arena whose live phase has reached the time cap; must not be {@code null}.
     */
    public MatchTimedOutEvent(Arena arena) {
        this.arena = arena;
    }

    /**
     * Returns the arena whose live phase has hit the time cap.
     *
     * @return the arena, never {@code null}.
     */
    public Arena arena() {
        return arena;
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
