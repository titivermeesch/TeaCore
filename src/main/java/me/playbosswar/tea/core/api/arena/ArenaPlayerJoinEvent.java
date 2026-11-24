package me.playbosswar.tea.core.api.arena;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired by {@link ArenaManager#addPlayer(ArenaId, Player, boolean)} after
 * the player's membership is recorded and they have been teleported in. Not
 * cancellable.
 */
public final class ArenaPlayerJoinEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final Player player;
    private final boolean spectator;

    public ArenaPlayerJoinEvent(Arena arena, Player player, boolean spectator) {
        this.arena = arena;
        this.player = player;
        this.spectator = spectator;
    }

    public Arena arena() { return arena; }

    public Player player() { return player; }

    public boolean spectator() { return spectator; }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
