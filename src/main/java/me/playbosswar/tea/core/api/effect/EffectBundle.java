package me.playbosswar.tea.core.api.effect;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A reusable combination of particles, sounds, and other one-shot visual
 * effects.
 *
 * <p>
 * Bundles are registered once with an id via
 * {@link EffectService#register(String, EffectBundle)}
 * and looked up at playback time. Implementations decide which viewers see the
 * effect based
 * on distance, line of sight, or explicit targeting.
 */
public interface EffectBundle {

    /**
     * Plays the bundle centered at a world location for all viewers in range.
     *
     * <p>
     * Runs on the main thread; spawns particles and plays sounds in the given
     * world.
     *
     * @param location world location to center the effect at; must not be
     *                 {@code null}.
     */
    void playAt(Location location);

    /**
     * Plays the bundle at a player's location, visible and audible to that player.
     *
     * <p>
     * Runs on the main thread. Implementations typically show the effect only to
     * the
     * specified player (first-person feedback).
     *
     * @param player player to play the effect for; must not be {@code null}.
     */
    void playFor(Player player);
}
