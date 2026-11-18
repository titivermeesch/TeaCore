package me.playbosswar.tea.core.api.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import org.jetbrains.annotations.Nullable;

/**
 * Finished matches, recorded by TeaCore when a match ends (game, map, winner,
 * duration, and each participant's team, kills, deaths, assists and result).
 * Turn recording off with {@code features.match-history: false}.
 *
 * <p>Queries hit the database; call them off the main thread when you can.
 */
public interface MatchHistoryService {

    /** One finished match. {@code winnerTeam}/{@code winnerUuid} are null for a draw. */
    record MatchRecord(UUID matchId, String gameId, @Nullable String mapId, @Nullable String serverId,
                       Instant endedAt, int durationSeconds, @Nullable String winnerTeam,
                       @Nullable UUID winnerUuid) { }

    /** One player's line in a match. {@code result} is {@code win}, {@code loss} or {@code draw}. */
    record PlayerMatch(MatchRecord match, @Nullable String team, int kills, int deaths, int assists,
                       String result) { }

    /** A player's most recent matches, newest first. */
    List<PlayerMatch> recentFor(UUID player, int limit);

    /** The most recent matches, newest first; {@code game} narrows to one game. */
    List<MatchRecord> recent(@Nullable GameId game, int limit);

    /** Every participant line of one match. */
    List<PlayerMatch> participants(UUID matchId);
}
