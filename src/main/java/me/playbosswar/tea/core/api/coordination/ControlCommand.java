package me.playbosswar.tea.core.api.coordination;

import org.jetbrains.annotations.Nullable;

/**
 * A fire-and-forget admin request for one server, published with
 * {@link CoordinationBackend#publishControl(String, ControlCommand)} and run by
 * the target server via
 * {@link CoordinationBackend#subscribeControl(java.util.function.Consumer)}.
 * Each {@link Type} uses a different subset of the nullable fields.
 *
 * @param arenaId    target arena, for commands that act on one arena.
 * @param gameId     target game id, for {@link Type#ALLOCATE_ARENA} and
 *                   {@link Type#DELETE_MAP}; {@code null} otherwise.
 * @param mapId      optional map id argument.
 * @param playerUuid optional player uuid argument (e.g. winner on end-match, the
 *                   player to kick or move).
 * @param teamId     winning team for {@link Type#END_MATCH}; {@code null} otherwise.
 * @param targetArenaId destination arena for {@link Type#MOVE_PLAYER}.
 * @param mode       what to do with an arriving player for {@link Type#EXPECT_PLAYER}:
 *                   {@code join}, {@code spectate}, {@code observe} or
 *                   {@code tp:<player uuid>}.
 */
public record ControlCommand(
        Type type,
        @Nullable String arenaId,
        @Nullable String gameId,
        @Nullable String mapId,
        @Nullable String playerUuid,
        @Nullable String teamId,
        @Nullable String targetArenaId,
        @Nullable String mode) {

    /**
     * A command without the team, target and mode fields. This is a factory, not
     * a constructor, so JSON deserialization has a single creator.
     */
    public static ControlCommand of(Type type, @Nullable String arenaId, @Nullable String gameId,
                                    @Nullable String mapId, @Nullable String playerUuid) {
        return new ControlCommand(type, arenaId, gameId, mapId, playerUuid, null, null, null);
    }

    public enum Type {
        START_ARENA,
        RELEASE_ARENA,
        /** Ends the match; the winner is {@code teamId}, else {@code playerUuid}, else a draw. */
        END_MATCH,
        ALLOCATE_ARENA,
        RELOAD_MAPS,
        DELETE_MAP,
        /** Restores version {@code mode} (1 = newest) of map {@code gameId}/{@code mapId}. */
        RESTORE_MAP,
        /** Removes {@code playerUuid} from its arena and sends them to the lobby. */
        KICK_PLAYER,
        /** Turns participant {@code playerUuid} into a spectator of the same arena. */
        MAKE_SPECTATOR,
        /** Moves {@code playerUuid} out of its arena into {@code targetArenaId}, on any server. */
        MOVE_PLAYER,
        /** {@code playerUuid} is on the way to this server; put them in {@code arenaId} per {@code mode}. */
        EXPECT_PLAYER,
        /** Applies setting changes; {@code mode} holds them as JSON ({@code file}, {@code actor}, {@code changes}). */
        APPLY_SETTINGS
    }
}
