package me.playbosswar.tea.core.api.arena;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by {@link ArenaManager#removePlayer(Player, LeaveReason)} after the
 * player's membership is cleared and they have been evacuated. Not cancellable.
 */
public final class ArenaPlayerLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final Player player;
    private final LeaveReason reason;

    public ArenaPlayerLeaveEvent(Arena arena, Player player, LeaveReason reason) {
        this.arena = arena;
        this.player = player;
        this.reason = reason;
    }

    public Arena arena() {
        return arena;
    }

    public Player player() {
        return player;
    }

    public LeaveReason reason() {
        return reason;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum LeaveReason {
        DISCONNECT,
        /** Player ran a leave command such as {@code /leave}. */
        COMMAND,
        /** Kicked by staff or an anti-cheat rule. */
        KICK,
        /** The match ended and the arena is evacuating everyone. */
        MATCH_ENDED
    }
}
