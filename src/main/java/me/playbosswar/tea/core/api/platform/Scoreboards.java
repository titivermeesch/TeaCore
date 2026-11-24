package me.playbosswar.tea.core.api.platform;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Scoreboard API that changed between versions. */
public interface Scoreboards {

    /** A new dummy objective with a display title. */
    Objective newObjective(Scoreboard board, String name, String title);

    /** Colours the names of the team's members (team colour on 1.12+, name prefix before). */
    void setTeamColor(Team team, ChatColor color);

    /** Longest team prefix / suffix: 16 before 1.13, 64 since. */
    int maxAffixLength();

    /** Longest objective title: 32 before 1.13, 128 since. */
    int maxTitleLength();
}
