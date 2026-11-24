package me.playbosswar.tea.core.api.sidebar;

import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.arena.Arena;
import org.bukkit.entity.Player;

/**
 * A game's contribution to its sidebar layout: extra {@code <tag>} values and
 * extra columns for {@code @players:} / {@code @teams:} rows. Everything here is
 * merged over TeaCore's built-in values, so a game can override them too.
 */
public interface SidebarData {

    SidebarData NONE = new SidebarData() { };

    /** Extra placeholders for one viewer, e.g. {@code coins -> 30}. */
    default Map<String, String> placeholders(Player viewer, Arena arena) {
        return Map.of();
    }

    /**
     * Extra columns for the {@code @players:} row of {@code player}, e.g.
     * {@code player-stage -> 3}.
     */
    default Map<String, String> playerRow(Player viewer, Arena arena, Player player) {
        return Map.of();
    }

    /**
     * Extra columns for the {@code @teams:} row of a team, e.g.
     * {@code team-score -> 12}.
     *
     * @param teamId the team's id value, e.g. {@code red}.
     */
    default Map<String, String> teamRow(Player viewer, Arena arena, String teamId) {
        return Map.of();
    }

    /** Order of the {@code @players:} rows. Defaults to join order. */
    default List<Player> sortPlayers(Arena arena, List<Player> players) {
        return players;
    }
}
