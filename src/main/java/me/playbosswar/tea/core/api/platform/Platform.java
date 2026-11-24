package me.playbosswar.tea.core.api.platform;

/**
 * Version-independent access to server API that differs between Minecraft
 * versions (1.8.8 to latest). Use it instead of version-specific Bukkit calls.
 *
 * <p>Where a version lacks something, each method documents its fallback
 * (something close, or a no-op); {@link #supports(Feature)} tells you up front.
 */
public interface Platform {

    ServerVersion version();

    /** Whether this server can do something natively; fallbacks exist either way. */
    boolean supports(Feature feature);

    ItemTags tags();

    Holograms holograms();

    Particles particles();

    EntityControl entities();

    Items items();

    Sounds sounds();

    Worlds worlds();

    Players players();

    Scoreboards scoreboards();

    Projectiles projectiles();

    Effects effects();

    /** Whether players and projectiles pass through the block (air, flowers, ...). */
    boolean isPassable(org.bukkit.block.Block block);

    enum Feature {
        /** Text displays (1.19.4+); otherwise holograms use armor stands. */
        TEXT_DISPLAYS,
        /** Off hand and the swap-hands key (1.9+). */
        OFF_HAND,
        /** The 1.9 attack cooldown. */
        ATTACK_COOLDOWN,
        /** RGB chat colours (1.16+); otherwise the nearest of the 16 colours. */
        HEX_COLORS,
        /** World heights below 0 (1.18+). */
        NEGATIVE_WORLD_HEIGHT,
        /** Particles with options, such as sized dust (1.13+). */
        PARTICLE_OPTIONS,
        /** Lingering potion clouds (1.9+). */
        EFFECT_CLOUDS
    }
}
