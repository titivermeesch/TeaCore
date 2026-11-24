package me.playbosswar.tea.core.api.sidebar;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Supplies sidebar content to {@link SidebarService}, which polls every provider
 * per player and shows the highest-priority one that applies.
 */
public interface SidebarProvider {

    /** Highest wins; ties go to the earliest registered. */
    default int priority() {
        return 0;
    }

    /** @return empty when this provider does not apply to the player right now */
    Optional<Sidebar> sidebarFor(Player player);

    /** Already-rendered legacy section-code text; lines top to bottom, blanks allowed. */
    record Sidebar(String title, List<String> lines) {
        public Sidebar {
            lines = List.copyOf(lines);
        }
    }
}
