package me.playbosswar.tea.core.api.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Supplies a game's stats to {@code /tea stats [player]}. Register it with
 * {@link StatsRegistry#register(StatsProvider)}.
 */
public interface StatsProvider {

    GameId gameId();

    /**
     * Called on demand and may query the database. Return an ordered map
     * (see {@link #newEntries()}) so the output order is stable.
     *
     * @return label to value; empty if the player has no stats for this game
     */
    Map<String, String> fetch(UUID playerUuid);

    /** @return a new empty {@link LinkedHashMap} */
    static Map<String, String> newEntries() {
        return new LinkedHashMap<>();
    }
}
