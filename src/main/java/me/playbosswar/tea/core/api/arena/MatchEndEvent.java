package me.playbosswar.tea.core.api.arena;

import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.team.TeamId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when the arena enters {@link ArenaState#ENDING}, before participants
 * are evacuated, so listeners still see the populated arena. Not cancellable.
 */
public final class MatchEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final @Nullable UUID winnerUuid;
    private final @Nullable TeamId winningTeam;

    public MatchEndEvent(Arena arena, @Nullable UUID winnerUuid) {
        this(arena, winnerUuid, null);
    }

    /** @param winnerUuid a representative member of {@code winningTeam} for team results */
    public MatchEndEvent(Arena arena, @Nullable UUID winnerUuid, @Nullable TeamId winningTeam) {
        this.arena = arena;
        this.winnerUuid = winnerUuid;
        this.winningTeam = winningTeam;
    }

    /**
     * Set when ended with {@link ArenaManager#endMatchForTeam(ArenaId, TeamId)};
     * every member of the team counts as a winner. Empty for solo results, draws or
     * no winner.
     */
    public Optional<TeamId> winningTeam() {
        return Optional.ofNullable(winningTeam);
    }

    public Arena arena() {
        return arena;
    }

    /** Empty for a draw or no winner. For team results, one representative member. */
    public Optional<UUID> winner() {
        return Optional.ofNullable(winnerUuid);
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
