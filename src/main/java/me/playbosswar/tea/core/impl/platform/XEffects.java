package me.playbosswar.tea.core.impl.platform;

import com.cryptomorin.xseries.XPotion;
import java.util.Locale;
import me.playbosswar.tea.core.api.platform.Effects;
import org.bukkit.potion.PotionEffectType;

public final class XEffects implements Effects {

    @Override
    public PotionEffectType type(String modernName) {
        return XPotion.of(modernName.toUpperCase(Locale.ROOT)).filter(XPotion::isSupported)
                .map(XPotion::get).orElse(null);
    }
}
