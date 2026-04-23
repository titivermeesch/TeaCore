package me.playbosswar.tea.core.api.game;

import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;

/**
 * What an {@link Arena} should do after its match has ended.
 *
 * <p>Chosen per game as part of {@link GameDefinition} and consulted by the lifecycle
 * ticker after {@link ArenaState#ENDING}.
 */
public enum EndMode {
    /** Drain players back to lobby, rollback map, return arena to waiting. */
    RESET,
    /** Drain players back to lobby, then shut the server down (e.g. k8s pod rotation). */
    SHUTDOWN
}
