package me.playbosswar.tea.core.impl.dashboard.dto;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Rich fields ({@code players}, {@code spectators}, {@code matchId},
 * {@code worldName}) are only filled for arenas on the dashboard's own server;
 * remote arenas arrive with empty rosters and null ids.
 *
 * @param mapId                 {@code null} for void-only.
 * @param phaseSecondsRemaining {@code -1} if unavailable.
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
