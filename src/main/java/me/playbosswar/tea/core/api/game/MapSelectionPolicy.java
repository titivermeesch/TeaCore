package me.playbosswar.tea.core.api.game;

/**
 * Policy used by {@link me.playbosswar.tea.core.api.arena.ArenaManager} to pick a map when
 * allocating a new arena for a game.
 *
 * <p>Sealed: the permitted subtypes are {@link Random} and {@link Fixed}. Use the
 * {@link #random()} / {@link #fixed(String)} factories for readability.
 */
public sealed interface MapSelectionPolicy {

    /**
     * Policy variant that picks a random map from the game's registered pool.
     */
    record Random() implements MapSelectionPolicy {
    }

    /**
     * Policy variant that always loads the given map id.
     *
     * @param mapId id of the map to load; never {@code null}.
     */
    record Fixed(String mapId) implements MapSelectionPolicy {
    }

    /**
     * Factory for the random-selection variant.
     *
     * @return a {@link Random} policy, never {@code null}.
     */
    static MapSelectionPolicy random() {
        return new Random();
    }

    /**
     * Factory for the fixed-map variant.
     *
     * @param mapId id of the map to always use; must not be {@code null}.
     * @return a {@link Fixed} policy, never {@code null}.
     */
    static MapSelectionPolicy fixed(String mapId) {
        return new Fixed(mapId);
    }
}
