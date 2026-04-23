package me.playbosswar.tea.core.impl.effect;

import java.util.List;
import me.playbosswar.tea.core.api.effect.EffectBundle;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Minimal effect bundle: list of particle and sound effects played together at
 * a location.
 */
public final class SimpleEffectBundle implements EffectBundle {

    public record SoundEffect(Sound sound, float volume, float pitch) {
    }

    public record ParticleEffect(Particle particle, int count, double dx, double dy, double dz, double speed) {
    }

    private final List<SoundEffect> sounds;
    private final List<ParticleEffect> particles;

    public SimpleEffectBundle(List<SoundEffect> sounds, List<ParticleEffect> particles) {
        this.sounds = List.copyOf(sounds);
        this.particles = List.copyOf(particles);
    }

    @Override
    public void playAt(Location location) {
        if (location.getWorld() == null)
            return;
        for (ParticleEffect p : particles) {
            location.getWorld().spawnParticle(p.particle(), location,
                    p.count(), p.dx(), p.dy(), p.dz(), p.speed());
        }
        for (SoundEffect s : sounds) {
            location.getWorld().playSound(location, s.sound(), s.volume(), s.pitch());
        }
    }

    @Override
    public void playFor(Player player) {
        playAt(player.getLocation());
    }
}
