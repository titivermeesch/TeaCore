package me.playbosswar.tea.core.api.arena;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a player has been removed from an {@link Arena}.
 *
 * <p>
 * Emitted by {@link ArenaManager#removePlayer(Player, LeaveReason)} once the
 * player's
 * arena membership has been cleared and they have been evacuated. Not
 * cancellable.
 */
public final class ArenaPlayerLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final Player player;
    private final LeaveReason reason;

    /**
     * Constructs a new event describing a player leaving an arena.
     *
     * @param arena  arena that was left; must not be {@code null}.
     * @param player player who left; must not be {@code null}.
     * @param reason reason for the departure; must not be {@code null}.
     */
    public ArenaPlayerLeaveEvent(Arena arena, Player player, LeaveReason reason) {
        this.arena = arena;
        this.player = player;
        this.reason = reason;
    }

    /**
     * Returns the arena the player left.
     *
     * @return the arena, never {@code null}.
     */
    public Arena arena() {
        return arena;
    }

    /**
     * Returns the player that left.
     *
     * @return the player, never {@code null}.
     */
    public Player player() {
        return player;
    }

    /**
     * Returns the reason for the departure.
     *
     * @return the reason, never {@code null}.
     */
    public LeaveReason reason() {
        return reason;
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

    /**
     * Enumerates why a player left an arena.
     */
    public enum LeaveReason {
        /** Player disconnected from the server. */
        DISCONNECT,
        /** Player ran a leave command (e.g. {@code /leave}). */
        COMMAND,
        /** Player was kicked by a staff member or anti-cheat rule. */
        KICK,
        /** Match ended and the arena is evacuating all occupants. */
        MATCH_ENDED
    }
}
