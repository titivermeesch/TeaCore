package me.playbosswar.tea.core.api.chat;

import java.util.Map;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.team.Team;

/**
 * Sends translated messages scoped to one arena or team, so broadcasts don't leak across
 * matches on a multi-arena server. Keys are resolved per recipient locale through
 * {@link me.playbosswar.tea.core.api.message.MessageService}.
 */
public interface MatchBroadcaster {

    /** Sends to participants and spectators. */
    void toMatch(Arena arena, String key, Map<String, String> placeholders);

    default void toMatch(Arena arena, String key) {
        toMatch(arena, key, Map.of());
    }

    void toTeam(Team team, String key, Map<String, String> placeholders);

    default void toTeam(Team team, String key) {
        toTeam(team, key, Map.of());
    }

    /** Sends to spectators only. */
    void toSpectators(Arena arena, String key, Map<String, String> placeholders);

    default void toSpectators(Arena arena, String key) {
        toSpectators(arena, key, Map.of());
    }

    /** Shows an action bar to participants and spectators. */
    void actionBarToMatch(Arena arena, String key, Map<String, String> placeholders);

    default void actionBarToMatch(Arena arena, String key) {
        actionBarToMatch(arena, key, Map.of());
    }
}
