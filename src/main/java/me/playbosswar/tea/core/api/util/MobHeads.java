package me.playbosswar.tea.core.api.util;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Maps {@link EntityType} values to a representative {@link Material} for use
 * as a visual
 * stand-in in inventories, scoreboards, or GUIs.
 *
 * <p>
 * Favors actual skull items when vanilla provides one, and otherwise falls back
 * to a
 * drop or body-part that evokes the mob. Games that morph, disguise, or
 * visualize mobs
 * should call {@link #iconFor(EntityType)} instead of hardcoding their own
 * mapping.
 *
 * @apiNote Utility holder; not instantiable.
 */
public final class MobHeads {

    private MobHeads() {
    }

    /**
     * Returns a material that visually represents the given entity type.
     *
     * <p>
     * Unknown or {@code null} types fall back to {@link Material#PAPER}.
     *
     * @param type entity type to represent, or {@code null}.
     * @return a material chosen as a stand-in, never {@code null}.
     */
    public static Material iconFor(@Nullable EntityType type) {
        if (type == null)
            return Material.PAPER;
        return switch (type) {
            case PLAYER -> Material.PLAYER_HEAD;
            case ZOMBIE, HUSK, DROWNED -> Material.ZOMBIE_HEAD;
            case SKELETON, STRAY -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN -> Material.PIGLIN_HEAD;
            case IRON_GOLEM -> Material.IRON_BLOCK;
            case SNOW_GOLEM -> Material.CARVED_PUMPKIN;
            case SLIME -> Material.SLIME_BLOCK;
            case MAGMA_CUBE -> Material.MAGMA_BLOCK;
            case SPIDER, CAVE_SPIDER -> Material.SPIDER_EYE;
            case BLAZE -> Material.BLAZE_POWDER;
            case ENDERMAN -> Material.ENDER_PEARL;
            case ENDERMITE -> Material.END_STONE;
            case CHICKEN -> Material.EGG;
            case PIG -> Material.PORKCHOP;
            case COW -> Material.BEEF;
            case MOOSHROOM -> Material.RED_MUSHROOM;
            case SHEEP -> Material.WHITE_WOOL;
            case HORSE, DONKEY, MULE -> Material.SADDLE;
            case WOLF -> Material.BONE;
            case CAT, OCELOT -> Material.STRING;
            case PARROT -> Material.FEATHER;
            case RABBIT -> Material.RABBIT_FOOT;
            case FOX -> Material.SWEET_BERRIES;
            case POLAR_BEAR -> Material.SNOW_BLOCK;
            case PANDA -> Material.BAMBOO;
            case BEE -> Material.HONEYCOMB;
            case TURTLE -> Material.TURTLE_SCUTE;
            case AXOLOTL -> Material.TROPICAL_FISH;
            case FROG -> Material.LILY_PAD;
            case WITCH -> Material.POTION;
            case VILLAGER, WANDERING_TRADER -> Material.EMERALD;
            case VINDICATOR, PILLAGER, EVOKER -> Material.CROSSBOW;
            case RAVAGER -> Material.SADDLE;
            case VEX, ALLAY -> Material.ECHO_SHARD;
            case GHAST -> Material.GHAST_TEAR;
            case PHANTOM -> Material.PHANTOM_MEMBRANE;
            case SHULKER -> Material.SHULKER_SHELL;
            case GUARDIAN, ELDER_GUARDIAN -> Material.PRISMARINE_SHARD;
            case SQUID, GLOW_SQUID -> Material.INK_SAC;
            case DOLPHIN -> Material.COD;
            case COD -> Material.COD;
            case SALMON -> Material.SALMON;
            case PUFFERFISH -> Material.PUFFERFISH;
            case TROPICAL_FISH -> Material.TROPICAL_FISH;
            case SILVERFISH -> Material.STONE;
            case BAT -> Material.COAL;
            case LLAMA, TRADER_LLAMA -> Material.LEATHER;
            case CAMEL -> Material.CACTUS;
            case SNIFFER -> Material.TORCHFLOWER_SEEDS;
            case GOAT -> Material.GOAT_HORN;
            case HOGLIN, ZOGLIN -> Material.PORKCHOP;
            case STRIDER -> Material.WARPED_FUNGUS_ON_A_STICK;
            case WITHER -> Material.WITHER_SKELETON_SKULL;
            case WARDEN -> Material.SCULK_SHRIEKER;
            default -> Material.PAPER;
        };
    }
}
