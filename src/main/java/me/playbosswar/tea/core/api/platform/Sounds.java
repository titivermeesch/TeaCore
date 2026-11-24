package me.playbosswar.tea.core.api.platform;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Sounds by their modern names (e.g. ENTITY_BLAZE_SHOOT), mapped on older versions. Unknown names are ignored.
 * Give fallbacks with "|": {@code "BLOCK_AMETHYST_BLOCK_CHIME|BLOCK_NOTE_BLOCK_CHIME"} plays the first one the server has.
 */
public interface Sounds {

    void play(Player player, String sound, float volume, float pitch);

    void play(Location at, String sound, float volume, float pitch);
}
