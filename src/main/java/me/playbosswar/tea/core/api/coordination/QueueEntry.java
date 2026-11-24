package me.playbosswar.tea.core.api.coordination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * A player waiting for the matchmaker leader to route them into an arena.
 *
 * @param playerName captured at enqueue time
 * @param mapIds     maps the player accepts; empty means any
 * @param rating     the player's rating in the game, for ranked entries
 * @param partyId    {@code null} without a party; members of one party go to the same arena
 * @param partySize  {@code 1} without a party
 */
public record QueueEntry(
        UUID playerUuid,
        String playerName,
        GameId gameId,
        List<String> mapIds,
        Instant queuedAt,
        boolean ranked,
        int rating,
        String partyId,
        int partySize) {

    public QueueEntry {
        mapIds = mapIds == null ? List.of() : List.copyOf(mapIds);
    }

    /** Unranked, no party. */
    public QueueEntry(UUID playerUuid, String playerName, GameId gameId, List<String> mapIds, Instant queuedAt) {
        this(playerUuid, playerName, gameId, mapIds, queuedAt, false, 0, null, 1);
    }

    /** No party. */
    public QueueEntry(UUID playerUuid, String playerName, GameId gameId, List<String> mapIds, Instant queuedAt,
                      boolean ranked, int rating) {
        this(playerUuid, playerName, gameId, mapIds, queuedAt, ranked, rating, null, 1);
    }

    public boolean acceptsAnyMap() {
        return mapIds.isEmpty();
    }
}
