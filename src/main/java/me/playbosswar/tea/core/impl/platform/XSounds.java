package me.playbosswar.tea.core.impl.platform;

import com.cryptomorin.xseries.XSound;
import java.util.Locale;
import java.util.Optional;
import me.playbosswar.tea.core.api.platform.Sounds;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class XSounds implements Sounds {

    /** The first name in "A|B|C" this version has. */
    private static Optional<XSound> sound(String names) {
        for (String name : names.split("\\|")) {
            Optional<XSound> s = XSound.of(name.trim().toUpperCase(Locale.ROOT)).filter(XSound::isSupported);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    @Override
    public void play(Player player, String sound, float volume, float pitch) {
        sound(sound).ifPresent(s -> {
            org.bukkit.Sound bukkit = s.get();
            if (bukkit != null) {
                player.playSound(player.getLocation(), bukkit, volume, pitch);
            }
        });
    }

    @Override
    public void play(Location at, String sound, float volume, float pitch) {
        sound(sound).ifPresent(s -> s.play(at, volume, pitch));
    }
}
