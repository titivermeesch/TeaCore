package me.playbosswar.tea.core.api.sidebar;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Contributes sidebar content to the {@link SidebarService}. A feature
 * implements this instead of standing up its own Bukkit scoreboard: the service
 * polls every registered provider per player and renders the highest-priority
 * one that applies.
 */
public interface SidebarProvider {

    /**
     * Relative precedence when more than one provider wants the same player's
     * sidebar. The highest value wins; ties resolve to the earliest registered.
     *
     * @return the priority; default {@code 0}.
     */
    default int priority() {
        return 0;
    }

    /**
     * Returns the sidebar this provider wants to show the player, or empty when
     * it does not apply to them right now.
     *
     * @param player the player to render for; never {@code null}.
     * @return the sidebar, or {@link Optional#empty()} to opt out.
     */
    Optional<Sidebar> sidebarFor(Player player);

    /**
     * A fully-rendered sidebar: a title and ordered lines, top to bottom, in
     * legacy section-code text (the manager only handles the Bukkit mechanics).
     *
     * @param title objective display name.
     * @param lines line text, rendered top-to-bottom; may contain blanks.
     */
    record Sidebar(String title, List<String> lines) {
        public Sidebar {
            lines = List.copyOf(lines);
        }
    }
}
