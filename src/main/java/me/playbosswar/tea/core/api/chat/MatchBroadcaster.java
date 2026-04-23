package me.playbosswar.tea.core.api.chat;

import java.util.Map;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.team.Team;

/**
 * Scoped message sender that keeps broadcasts inside an arena or team.
 *
 * <p>Games should prefer this interface over {@code Bukkit.broadcast(...)} so messages do
 * not leak across matches on multi-arena servers. Messages are looked up by translation
 * key via {@link me.playbosswar.tea.core.api.message.MessageService} so they respect each
 * recipient's locale.
 */
public interface MatchBroadcaster {

    /**
     * Sends a localized chat message to every participant and spectator in an arena.
     *
     * @param arena        arena whose occupants receive the message; must not be {@code null}.
     * @param key          translation key to render; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never {@code null}.
     */
    void toMatch(Arena arena, String key, Map<String, String> placeholders);

    /**
     * Sends a localized chat message with no placeholders to every occupant of an arena.
     *
     * <p>Equivalent to
     * {@link #toMatch(Arena, String, Map) toMatch(arena, key, Map.of())}.
     *
     * @param arena arena whose occupants receive the message; must not be {@code null}.
     * @param key   translation key to render; must not be {@code null}.
     */
    default void toMatch(Arena arena, String key) {
        toMatch(arena, key, Map.of());
    }

    /**
     * Sends a localized chat message to every member of a team.
     *
     * @param team         team whose members receive the message; must not be {@code null}.
     * @param key          translation key to render; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never {@code null}.
     */
    void toTeam(Team team, String key, Map<String, String> placeholders);

    /**
     * Sends a localized chat message with no placeholders to every member of a team.
     *
     * <p>Equivalent to {@link #toTeam(Team, String, Map) toTeam(team, key, Map.of())}.
     *
     * @param team team whose members receive the message; must not be {@code null}.
     * @param key  translation key to render; must not be {@code null}.
     */
    default void toTeam(Team team, String key) {
        toTeam(team, key, Map.of());
    }

    /**
     * Sends a localized chat message to the spectators of an arena only.
     *
     * @param arena        arena whose spectators receive the message; must not be {@code null}.
     * @param key          translation key to render; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never {@code null}.
     */
    void toSpectators(Arena arena, String key, Map<String, String> placeholders);

    /**
     * Sends a localized chat message with no placeholders to spectators of an arena.
     *
     * <p>Equivalent to
     * {@link #toSpectators(Arena, String, Map) toSpectators(arena, key, Map.of())}.
     *
     * @param arena arena whose spectators receive the message; must not be {@code null}.
     * @param key   translation key to render; must not be {@code null}.
     */
    default void toSpectators(Arena arena, String key) {
        toSpectators(arena, key, Map.of());
    }

    /**
     * Sends a localized message as an action-bar overlay to every player and spectator in
     * an arena.
     *
     * @param arena        arena whose occupants receive the overlay; must not be {@code null}.
     * @param key          translation key to render; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never {@code null}.
     */
    void actionBarToMatch(Arena arena, String key, Map<String, String> placeholders);

    /**
     * Sends a localized action-bar overlay with no placeholders to every occupant of an arena.
     *
     * <p>Equivalent to
     * {@link #actionBarToMatch(Arena, String, Map) actionBarToMatch(arena, key, Map.of())}.
     *
     * @param arena arena whose occupants receive the overlay; must not be {@code null}.
     * @param key   translation key to render; must not be {@code null}.
     */
    default void actionBarToMatch(Arena arena, String key) {
        actionBarToMatch(arena, key, Map.of());
    }
}
