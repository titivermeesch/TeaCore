package me.playbosswar.tea.core.api.coordination;

import org.jetbrains.annotations.Nullable;

/**
 * A fire-and-forget operational request sent to a specific backend server.
 *
 * <p>
 * The dashboard (running on the lobby backend) publishes commands via
 * {@link CoordinationBackend#publishControl(String, ControlCommand)} so admins
 * can force-start, release, end-match, allocate, or delete maps on any server
 * in the fleet. The target server picks them up through
 * {@link CoordinationBackend#subscribeControl(java.util.function.Consumer)} and
 * executes them locally. Fields are nullable because different command types
 * use different subsets.
 *
 * @param type       the command to execute; never {@code null}.
 * @param arenaId    target arena id as string, for commands that act on a
 *                   specific arena; {@code null} otherwise.
 * @param gameId     target game id, for {@link Type#ALLOCATE_ARENA} and
 *                   {@link Type#DELETE_MAP}; {@code null} otherwise.
 * @param mapId      optional map id argument.
 * @param playerUuid optional player uuid argument (e.g. winner on end-match).
 */
public record ControlCommand(
        Type type,
        @Nullable String arenaId,
        @Nullable String gameId,
        @Nullable String mapId,
        @Nullable String playerUuid) {

    public enum Type {
        START_ARENA,
        RELEASE_ARENA,
        END_MATCH,
        ALLOCATE_ARENA,
        RELOAD_MAPS,
        DELETE_MAP
    }
}
