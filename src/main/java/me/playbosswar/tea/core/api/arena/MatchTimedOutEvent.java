package me.playbosswar.tea.core.api.arena;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired once per match when the {@link ArenaState#LIVE} phase reaches
 * {@code maxMatchSeconds}. Not cancellable.
 *
 * <p>The game must pick a winner or draw and call
 * {@link ArenaManager#endMatch(ArenaId, java.util.UUID)}; otherwise the arena stays
 * {@link ArenaState#LIVE}.
 */
public final class MatchTimedOutEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;

    public MatchTimedOutEvent(Arena arena) {
        this.arena = arena;
    }

    public Arena arena() {
        return arena;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
