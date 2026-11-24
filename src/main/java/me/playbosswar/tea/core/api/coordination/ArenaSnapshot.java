package me.playbosswar.tea.core.api.coordination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * One arena's state as published by the server that owns it. Matchmakers read
 * these via {@link CoordinationBackend#allArenas()} to route queued players.
 *
 * @param mapId     selected map id, or {@code ""} for a void-only arena.
 * @param players   participant count, excluding spectators.
 * @param updatedAt when the snapshot was produced; the backend expires stale ones.
 * @param secondsRemaining seconds left in the current phase, or {@code -1} if unknown.
 * @param roster    participants and spectators; never {@code null}.
 * @param sidebar   the sidebar as one participant sees it, or {@code null} if the
 *                  game shows none.
 * @param ranked    {@code null} until the first player decides whether the arena
 *                  is ranked.
 */
public record ArenaSnapshot(
        String serverId,
        ArenaId arenaId,
        GameId gameId,
        String mapId,
        ArenaState state,
        int players,
        int minPlayers,
        int maxPlayers,
        Instant updatedAt,
        int secondsRemaining,
        List<RosterEntry> roster,
        SidebarView sidebar,
        Boolean ranked,
        int averageRating,
        int ratingRange) {

    public ArenaSnapshot {
        roster = roster == null ? List.of() : List.copyOf(roster);
    }

    /** A snapshot without ranked information (casual or undecided). */
    public ArenaSnapshot(String serverId, ArenaId arenaId, GameId gameId, String mapId, ArenaState state,
                         int players, int minPlayers, int maxPlayers, Instant updatedAt, int secondsRemaining,
                         List<RosterEntry> roster, SidebarView sidebar) {
        this(serverId, arenaId, gameId, mapId, state, players, minPlayers, maxPlayers, updatedAt, secondsRemaining,
                roster, sidebar, null, 0, 0);
    }

    /**
     * Whether a queued player may be routed here as far as ranked play goes:
     * casual players skip ranked arenas, ranked players skip casual ones and
     * ranked arenas whose average rating is outside the arena's window.
     * Arenas nobody has joined yet ({@code ranked} null) take anyone.
     */
    public boolean acceptsRanked(boolean wantsRanked, int rating) {
        if (ranked == null || players == 0) {
            return true;
        }
        if (ranked != wantsRanked) {
            return false;
        }
        return !wantsRanked || Math.abs(averageRating - rating) <= ratingRange;
    }

    /** A snapshot without timer, roster or sidebar. */
    public ArenaSnapshot(String serverId, ArenaId arenaId, GameId gameId, String mapId, ArenaState state,
                         int players, int minPlayers, int maxPlayers, Instant updatedAt) {
        this(serverId, arenaId, gameId, mapId, state, players, minPlayers, maxPlayers, updatedAt, -1, List.of(),
                null);
    }

    /**
     * @param team  team id, or {@code null} when not on a team.
     * @param kills kills this match; likewise {@code deaths} and {@code assists}.
     */
    public record RosterEntry(UUID uuid, String name, String team, boolean spectator,
                              int kills, int deaths, int assists) {

        public RosterEntry(UUID uuid, String name, String team, boolean spectator) {
            this(uuid, name, team, spectator, 0, 0, 0);
        }
    }

    /**
     * A sidebar as one player sees it. Lines keep their legacy colour codes.
     *
     * @param viewer name of the player it was rendered for.
     * @param lines  top to bottom.
     */
    public record SidebarView(String viewer, String title, List<String> lines) {
        public SidebarView {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * True while in {@link ArenaState#WAITING} or {@link ArenaState#COUNTDOWN}
     * and below {@link #maxPlayers()}. Ignores ranked rules; see
     * {@link #acceptsRanked(boolean, int)}.
     */
    public boolean isAvailable() {
        if (state != ArenaState.WAITING && state != ArenaState.COUNTDOWN) return false;
        return players < maxPlayers;
    }
}
