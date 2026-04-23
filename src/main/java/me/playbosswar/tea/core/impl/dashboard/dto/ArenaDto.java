package me.playbosswar.tea.core.impl.dashboard.dto;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Serialized view of an arena for the dashboard.
 *
 * <p>
 * Built from a network-wide {@code ArenaSnapshot} published by the owning
 * backend, so most rich fields ({@code players}, {@code spectators},
 * {@code matchId}, {@code worldName}) are only populated when the dashboard
 * queries an arena hosted by its own server. For arenas on remote pods the
 * rosters arrive empty and the dashboard renders just the state/count summary.
 *
 * @param id                    arena uuid.
 * @param gameId                owning game id.
 * @param serverId              backend currently hosting the arena.
 * @param mapId                 selected map id, or {@code null} for void-only.
 * @param state                 current {@code ArenaState} enum name.
 * @param matchId               match uuid when available locally;
 *                              {@code null} for remote arenas.
 * @param phaseSecondsRemaining phase timer remaining, or {@code -1} if
 *                              unavailable.
 * @param minPlayers            min player count for this arena.
 * @param maxPlayers            max player count for this arena.
 * @param players               participant roster, empty for remote arenas.
 * @param spectators            spectator roster, empty for remote arenas.
 * @param worldName             Bukkit world name when arena is local;
 *                              {@code null} otherwise.
 */
public record ArenaDto(
        String id,
        String gameId,
        String serverId,
        @Nullable String mapId,
        String state,
        @Nullable String matchId,
        int phaseSecondsRemaining,
        int minPlayers,
        int maxPlayers,
        List<PlayerRefDto> players,
        List<PlayerRefDto> spectators,
        @Nullable String worldName) {
}
