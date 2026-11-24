package me.playbosswar.tea.core.impl.placeholder;

import java.util.Map;

/** Only used when no column has that name, so a game's own {@code wins} column always wins. */
final class StatAliases {

    static final Map<String, String> ALIASES = Map.of(
            "wins", "games_won",
            "played", "games_played",
            "games", "games_played");

    private StatAliases() {
    }

    static String target(String slug) {
        return ALIASES.get(slug);
    }

    /** {@code ascend_wins} -> {@code ascend_games_won}, or {@code null} if it doesn't end in an alias. */
    static String board(String board) {
        for (Map.Entry<String, String> e : ALIASES.entrySet()) {
            if (board.endsWith("_" + e.getKey())) {
                return board.substring(0, board.length() - e.getKey().length()) + e.getValue();
            }
        }
        return null;
    }
}
