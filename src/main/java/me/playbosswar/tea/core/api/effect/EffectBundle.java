package me.playbosswar.tea.core.api.effect;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A reusable combination of particles, sounds and other one-shot effects,
 * registered via {@link EffectService#register(String, EffectBundle)}.
 * Implementations choose which viewers see it.
 */
public interface EffectBundle {

    /** Plays for all viewers in range. Main thread only. */
    void playAt(Location location);

    /** Plays at the player's location, typically only for that player. Main thread only. */
    void playFor(Player player);
}
