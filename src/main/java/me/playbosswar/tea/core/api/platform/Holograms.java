package me.playbosswar.tea.core.api.platform;

import java.util.List;
import org.bukkit.Location;

/**
 * Floating text. Text displays on 1.19.4+, a stack of invisible named armor
 * stands before that. Lines are legacy-formatted strings (§ colours).
 */
public interface Holograms {

    Hologram create(Location location, List<String> lines);

    /** A floating text you can update or remove. */
    interface Hologram {
        void setLines(List<String> lines);

        void teleport(Location location);

        Location location();

        boolean isValid();

        void remove();
    }
}
