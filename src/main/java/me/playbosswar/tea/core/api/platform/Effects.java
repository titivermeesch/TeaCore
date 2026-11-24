package me.playbosswar.tea.core.api.platform;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/** Potion effects by their modern names (SLOWNESS, NAUSEA, ...), mapped on older versions. */
public interface Effects {

    /** The effect type, or null if this version has nothing like it. */
    @Nullable PotionEffectType type(String modernName);
}
