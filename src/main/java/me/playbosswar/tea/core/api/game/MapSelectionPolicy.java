package me.playbosswar.tea.core.api.game;

/** How {@link me.playbosswar.tea.core.api.arena.ArenaManager} picks a map for a new arena. */
public sealed interface MapSelectionPolicy {

    /** A random map from the game's registered maps. */
    record Random() implements MapSelectionPolicy {
    }

    record Fixed(String mapId) implements MapSelectionPolicy {
    }

    static MapSelectionPolicy random() {
        return new Random();
    }

    static MapSelectionPolicy fixed(String mapId) {
        return new Fixed(mapId);
    }
}
