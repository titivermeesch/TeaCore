package me.playbosswar.tea.core.api.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.jetbrains.annotations.Nullable;

/**
 * Finished matches, recorded by TeaCore when a match ends. Disabled by
 * {@code features.match-history: false}.
 *
 * <p>Queries hit the database; call them off the main thread when you can.
 */
public interface MatchHistoryService {

    /** One finished match. {@code winnerTeam}/{@code winnerUuid} are null for a draw. */
    record MatchRecord(UUID matchId, String gameId, @Nullable String mapId, @Nullable String serverId,
                       Instant endedAt, int durationSeconds, @Nullable String winnerTeam,
                       @Nullable UUID winnerUuid) { }

    /**
     * {@code result} is {@code win}, {@code loss}, {@code draw} or {@code left}
     * (left before the end). {@code name} is the name at the time, and may be
     * {@code null} for old records.
     */
    record PlayerMatch(MatchRecord match, @Nullable String team, int kills, int deaths, int assists,
                       String result, @Nullable UUID uuid, @Nullable String name) { }

    /**
     * Filters for {@link #search}. A {@code null} field matches anything.
     *
     * @param player a participant's name (case-insensitive) or uuid
     * @param from   ended at or after (inclusive)
     * @param to     ended before (exclusive)
     * @param limit  page size, 1-500
     */
    record Query(@Nullable String game, @Nullable String map, @Nullable String player,
                 @Nullable Instant from, @Nullable Instant to, int limit, int offset) { }

    /** Newest first. */
    List<MatchRecord> search(Query query);

    /** Ignores limit and offset. */
    int count(Query query);

    /** @return the number of matches deleted */
    int pruneBefore(Instant cutoff);

    /** Newest first. */
    List<PlayerMatch> recentFor(UUID player, int limit);

    /** Newest first; a {@code null} game means all games. */
    List<MatchRecord> recent(@Nullable GameId game, int limit);

    List<PlayerMatch> participants(UUID matchId);
}
