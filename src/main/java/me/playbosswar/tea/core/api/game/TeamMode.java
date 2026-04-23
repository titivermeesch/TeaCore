package me.playbosswar.tea.core.api.game;

/**
 * Declares how a game groups participants into teams.
 *
 * <p>Selected per game via {@link GameDefinition}; the value determines whether
 * {@link GameDefinition#teams()} must be non-empty.
 */
public enum TeamMode {
    /** Every player is their own team. */
    FFA,
    /** Multiple fixed teams declared by the game. */
    TEAMS
}
