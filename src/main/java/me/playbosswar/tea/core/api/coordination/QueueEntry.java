package me.playbosswar.tea.core.api.coordination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * A player waiting to be routed into a game arena.
 *
 * <p>
 * Queue entries are stored by the {@link CoordinationBackend} and consumed by
 * the
 * matchmaker leader. The {@code mapIds} list lets a player pre-filter which
 * maps they are
 * willing to play; an empty list accepts any map for the game.
 *
 * @param playerUuid uuid of the queued player; never {@code null}.
 * @param playerName display name captured at enqueue time, used for lobby UI;
 *                   never {@code null}.
 * @param gameId     game the player is queued for; never {@code null}.
 * @param mapIds     explicit list of map ids the player will accept; empty
 *                   means any map
 *                   for the game. Defensively copied and never {@code null}
 *                   after construction.
 * @param queuedAt   timestamp the entry was created; never {@code null}.
 */
public record QueueEntry(
        UUID playerUuid,
        String playerName,
        GameId gameId,
        List<String> mapIds,
        Instant queuedAt) {

    /**
     * Canonical constructor that defensively copies {@code mapIds} and normalizes
     * {@code null} to {@link List#of()}.
     */
    public QueueEntry {
        mapIds = mapIds == null ? List.of() : List.copyOf(mapIds);
    }

    /**
     * Indicates whether the player has not restricted the map choice.
     *
     * @return {@code true} if {@link #mapIds()} is empty; the matchmaker may pick
     *         any map.
     */
    public boolean acceptsAnyMap() {
        return mapIds.isEmpty();
    }
}
