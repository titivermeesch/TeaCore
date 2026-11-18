package me.playbosswar.tea.core.api.combat;

/**
 * A player's kills, deaths and assists in the current match of their arena.
 * Reset when the arena resets; still readable while the match is ending.
 */
public record MatchStats(int kills, int deaths, int assists) {

    public static final MatchStats ZERO = new MatchStats(0, 0, 0);
}
