package me.playbosswar.tea.core.api.sidebar;

import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Per-player sidebars for every Tea game and the lobby.
 *
 * <p>
 * Most games only call {@link #registerGame}: TeaCore then shows the game's
 * layout to everyone in one of its arenas, picking the list that matches the
 * arena phase. A layout section looks like:
 *
 * <pre>
 * sidebar:
 *   enabled: true
 *   title: "&lt;primary&gt;&lt;bold&gt;MyGame"
 *   waiting:   [ "&lt;secondary&gt;Players: &lt;highlight&gt;&lt;players&gt;/&lt;max-players&gt;" ]
 *   countdown: [ "&lt;success&gt;Starts in &lt;seconds&gt;s" ]
 *   live:
 *     - "&lt;secondary&gt;Time: &lt;accent&gt;&lt;time&gt;"
 *     - "@players: &lt;player-name&gt; &lt;muted&gt;&lt;player-kills&gt; kills"
 *     - "@teams: &lt;team-name&gt;: &lt;team-score&gt;"
 *   ended:     [ "&lt;winner&gt; &lt;success&gt;wins!" ]
 * </pre>
 *
 * Lines are MiniMessage with theme tags. {@code <tag>} placeholders come from
 * TeaCore (see the TeaDocs sidebar page for the full list) plus whatever the
 * game's {@link SidebarData} adds, and {@code %papi%} placeholders are resolved
 * when PlaceholderAPI is installed. A line starting with {@code @players:} is
 * repeated per participant and {@code @teams:} per team. A missing phase list
 * falls back to {@code live}; {@code starting} falls back to {@code countdown}.
 *
 * <p>
 * Sidebars can be turned off server-wide ({@code features.sidebar}), per game
 * ({@code features.games.<id>.sidebar}), per layout ({@code enabled: false}) and
 * per player ({@code /board}).
 */
public interface SidebarService {

    /**
     * Shows {@code layout} to players in arenas of {@code game}. Calling again
     * replaces it, e.g. after a config reload.
     *
     * @param layout the {@code sidebar} config section described above.
     * @param data   extra placeholders and loop rows, or {@link SidebarData#NONE}.
     */
    void registerGame(Plugin owner, GameId game, ConfigurationSection layout, SidebarData data);

    void unregisterGame(GameId game);

    /**
     * Adds a custom provider for cases a layout cannot express. The highest
     * priority provider that returns a sidebar wins; game layouts use priority
     * 100, the lobby layout 50.
     */
    void registerProvider(SidebarProvider provider);

    /** Hides or shows sidebars for one player (the {@code /board} toggle). */
    void setHidden(Player player, boolean hidden);

    /** Whether the player turned their sidebar off. */
    boolean isHidden(Player player);

    /** Re-renders a player's sidebar now instead of on the next tick. */
    void refresh(Player player);
}
