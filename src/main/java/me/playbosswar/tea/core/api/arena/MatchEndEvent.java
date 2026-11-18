package me.playbosswar.tea.core.api.arena;

import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.team.TeamId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a match in an {@link Arena} ends.
 *
 * <p>Emitted by {@link ArenaManager#endMatch(ArenaId, UUID)} or
 * {@link ArenaManager#endMatchForTeam(ArenaId, TeamId)} as the arena enters
 * {@link ArenaState#ENDING}, BEFORE participants are evacuated. Listeners can inspect
 * final state, record stats, or broadcast results while the arena is still populated. Not
 * cancellable.
 */
public final class MatchEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final @Nullable UUID winnerUuid;
    private final @Nullable TeamId winningTeam;

    /**
     * Constructs a new match-end event.
     *
     * @param arena      arena whose match is ending; must not be {@code null}.
     * @param winnerUuid uuid of the winning player, or {@code null} for a draw or no winner.
     */
    public MatchEndEvent(Arena arena, @Nullable UUID winnerUuid) {
        this(arena, winnerUuid, null);
    }

    /**
     * Constructs a match-end event for a team result.
     *
     * @param arena       arena whose match is ending; must not be {@code null}.
     * @param winnerUuid  a representative winning player, or {@code null}.
     * @param winningTeam the winning team, or {@code null} for a draw or no winner.
     */
    public MatchEndEvent(Arena arena, @Nullable UUID winnerUuid, @Nullable TeamId winningTeam) {
        this.arena = arena;
        this.winnerUuid = winnerUuid;
        this.winningTeam = winningTeam;
    }

    /**
     * Returns the winning team when the match was ended with
     * {@link ArenaManager#endMatchForTeam(ArenaId, TeamId)}. Every member of
     * that team counts as a winner.
     *
     * @return the winning team, or empty for solo results, draws or no winner.
     */
    public Optional<TeamId> winningTeam() {
        return Optional.ofNullable(winningTeam);
    }

    /**
     * Returns the arena whose match is ending.
     *
     * @return the arena, never {@code null}.
     */
    public Arena arena() {
        return arena;
    }

    /**
     * Returns the winning player's uuid if one was determined.
     *
     * @return the winner uuid, or empty for a draw or no winner.
     */
    public Optional<UUID> winner() {
        return Optional.ofNullable(winnerUuid);
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
