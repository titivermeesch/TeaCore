package me.playbosswar.tea.core.api.arena;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a player has been placed into an {@link Arena}.
 *
 * <p>Emitted by {@link ArenaManager#addPlayer(java.util.UUID, Player, boolean)} once the
 * player's arena membership is recorded and they have been teleported in. This event is
 * informational and not cancellable.
 */
public final class ArenaPlayerJoinEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final Player player;
    private final boolean spectator;

    /**
     * Constructs a new event describing a player joining an arena.
     *
     * @param arena     arena being joined; must not be {@code null}.
     * @param player    player who joined; must not be {@code null}.
     * @param spectator {@code true} if the player joined as a spectator, {@code false} as a
     *                  participant.
     */
    public ArenaPlayerJoinEvent(Arena arena, Player player, boolean spectator) {
        this.arena = arena;
        this.player = player;
        this.spectator = spectator;
    }

    /**
     * Returns the arena that was joined.
     *
     * @return the arena, never {@code null}.
     */
    public Arena arena() { return arena; }

    /**
     * Returns the player that joined.
     *
     * @return the player, never {@code null}.
     */
    public Player player() { return player; }

    /**
     * Indicates whether the player joined as a spectator.
     *
     * @return {@code true} for spectator, {@code false} for participant.
     */
    public boolean spectator() { return spectator; }

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
