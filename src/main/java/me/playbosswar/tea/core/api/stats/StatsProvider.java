package me.playbosswar.tea.core.api.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Per-game stats provider.
 *
 * <p>
 * Games register their own instance via
 * {@link StatsRegistry#register(StatsProvider)} so
 * {@code /tea stats [player]} can print totals without the core plugin knowing
 * their
 * schema. Providers are consulted on-demand and may hit the database.
 */
public interface StatsProvider {

    /**
     * Returns the game this provider belongs to.
     *
     * @return the game id, never {@code null}.
     */
    GameId gameId();

    /**
     * Returns a labeled, ordered map of stat values for a player.
     *
     * <p>
     * Implementations typically query the database and should return a
     * {@link LinkedHashMap} (see {@link #newEntries()}) so the output order is
     * stable in
     * command output.
     *
     * @param playerUuid player whose stats to fetch; must not be {@code null}.
     * @return an ordered map of label-to-value pairs; never {@code null}. Empty
     *         when the
     *         player has no recorded stats for this game.
     */
    Map<String, String> fetch(UUID playerUuid);

    /**
     * Convenience factory for {@link LinkedHashMap}-backed entry maps.
     *
     * <p>
     * Provider implementations that care about iteration order can start with this
     * and
     * return it from {@link #fetch(UUID)}.
     *
     * @return a new empty {@link LinkedHashMap}, never {@code null}.
     */
    static Map<String, String> newEntries() {
        return new LinkedHashMap<>();
    }
}
