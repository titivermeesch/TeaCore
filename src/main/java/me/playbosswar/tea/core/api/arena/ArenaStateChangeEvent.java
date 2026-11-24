package me.playbosswar.tea.core.api.arena;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after the arena has entered its new {@link ArenaState}, so a listener
 * for {@link ArenaState#LIVE} can read live match state. Not cancellable.
 */
public final class ArenaStateChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final ArenaState from;
    private final ArenaState to;

    public ArenaStateChangeEvent(Arena arena, ArenaState from, ArenaState to) {
        this.arena = arena;
        this.from = from;
        this.to = to;
    }

    public Arena arena() {
        return arena;
    }

    public ArenaState from() {
        return from;
    }

    public ArenaState to() {
        return to;
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
