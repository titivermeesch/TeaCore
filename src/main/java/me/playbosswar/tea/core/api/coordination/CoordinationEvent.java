package me.playbosswar.tea.core.api.coordination;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Flat, serializable arena or match event broadcast to every server through
 * {@link CoordinationBackend#publishEvent(CoordinationEvent)}. Each server
 * mirrors its local Bukkit events into these; the lobby dashboard turns them
 * into a live feed. Which nullable fields are set depends on {@link Type}.
 *
 * @param fromState             set for {@link Type#ARENA_STATE}
 * @param toState               set for {@link Type#ARENA_STATE}
 * @param spectator             set for {@link Type#ARENA_PLAYER_JOIN}
 * @param reason                set for {@link Type#ARENA_PLAYER_LEAVE}
 * @param winnerUuid            set for {@link Type#MATCH_END}
 * @param playerCount           live participant count after the event
 * @param phaseSecondsRemaining phase timer after an {@link Type#ARENA_STATE} transition; {@code -1} otherwise
 * @param data                  extra facts for {@link Type#KILL} ({@code killer}, {@code killerName},
 *                              {@code assists}, {@code cause}), {@link Type#CHAT} ({@code message})
 *                              and {@link Type#SERVER_STATS}
 */
public record CoordinationEvent(
        Type type,
        String serverId,
        @Nullable String arenaId,
        @Nullable String gameId,
        @Nullable String fromState,
        @Nullable String toState,
        @Nullable String playerUuid,
        @Nullable String playerName,
        @Nullable Boolean spectator,
        @Nullable String reason,
        @Nullable String winnerUuid,
        int playerCount,
        int phaseSecondsRemaining,
        @Nullable Map<String, String> data) {

    public enum Type {
        ARENA_STATE,
        ARENA_PLAYER_JOIN,
        ARENA_PLAYER_LEAVE,
        MATCH_END,
        /** A player died in a live arena; {@code playerUuid}/{@code playerName} is the victim. */
        KILL,
        /** A player in an arena sent a chat message; {@code playerUuid}/{@code playerName} is the sender. */
        CHAT,
        /**
         * Sent by every server every 15 seconds for metrics. {@code data}: {@code tps},
         * {@code mspt}, {@code players}, {@code memoryUsed}, {@code memoryMax} (bytes),
         * {@code spareWorlds}; {@code playerCount} is the online player count.
         */
        SERVER_STATS,
        /**
         * The settings files this server can edit: {@code data.files} holds their
         * schemas and current values as JSON.
         */
        SETTINGS,
        /** A server saved shared files (maps, menus...) to the database; the others pull them. */
        FILES_CHANGED
    }
}
