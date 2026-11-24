package me.playbosswar.tea.core.api.game;

import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.Arena;

/** What an {@link Arena} does after {@link ArenaState#ENDING}. */
public enum EndMode {
    /** Sends players to the lobby, rolls back the map and returns the arena to waiting. */
    RESET,
    /** Sends players to the lobby, then shuts the server down (e.g. for pod rotation). */
    SHUTDOWN
}
