package me.playbosswar.tea.core.impl.effect;

import java.util.List;
import me.playbosswar.tea.core.api.effect.EffectBundle;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SimpleEffectBundle implements EffectBundle {

    /** @param sound modern sound name, e.g. ENTITY_BLAZE_SHOOT */
    public record SoundEffect(String sound, float volume, float pitch) {
    }

    /** @param particle modern particle name, e.g. FLAME */
    public record ParticleEffect(String particle, int count, double dx, double dy, double dz, double speed) {
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
        var platform = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform();
        for (ParticleEffect p : particles) {
            platform.particles().spawn(p.particle(), location, p.count(), p.dx(), p.dy(), p.dz(), p.speed(), null);
        }
        for (SoundEffect s : sounds) {
            platform.sounds().play(location, s.sound(), s.volume(), s.pitch());
        }
    }

    @Override
    public void playFor(Player player) {
        playAt(player.getLocation());
    }
}
