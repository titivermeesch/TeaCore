package me.playbosswar.tea.core.api.combat;

import me.playbosswar.tea.core.api.arena.Arena;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a participant of a live arena dies, after {@link CombatTracker}
 * has resolved the killer and assisters. Fired once per death, whether the game
 * reported it via {@link CombatTracker#reportKill} or the player really died.
 * Not cancellable; the death has already happened.
 */
public final class ArenaKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final KillCredit credit;

    public ArenaKillEvent(Arena arena, KillCredit credit) {
        this.arena = arena;
        this.credit = credit;
    }

    /** The arena the death happened in. */
    public Arena arena() {
        return arena;
    }

    /** Victim, killer, assisters and cause. */
    public KillCredit credit() {
        return credit;
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
