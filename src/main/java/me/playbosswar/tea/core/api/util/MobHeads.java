package me.playbosswar.tea.core.api.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Picks an item that represents a mob in menus and scoreboards: its vanilla
 * skull when one exists, otherwise a drop or block that evokes it.
 */
public final class MobHeads {

    private MobHeads() {
    }

    /** Unknown or {@code null} types return {@link Material#PAPER}. */
    public static Material iconFor(@Nullable EntityType type) {
        ItemStack item = iconItemFor(type);
        return item.getType();
    }

    /**
     * Like {@link #iconFor(EntityType)}, but as an item. Prefer this on 1.8 to
     * 1.12, where all heads share one material and differ by data value.
     */
    public static ItemStack iconItemFor(@Nullable EntityType type) {
        String item = type == null ? "PAPER" : nameFor(type.name());
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().stackOr(item, "PAPER", 1);
    }

    private static String nameFor(String type) {
        // Pre-1.13 entity names that were renamed later.
        type = switch (type) {
            case "PIG_ZOMBIE" -> "ZOMBIFIED_PIGLIN";
            case "SNOWMAN" -> "SNOW_GOLEM";
            case "MUSHROOM_COW" -> "MOOSHROOM";
            case "VILLAGER_GOLEM" -> "IRON_GOLEM";
            default -> type;
        };
        return switch (type) {
            case "PLAYER" -> "PLAYER_HEAD";
            case "ZOMBIE", "HUSK", "DROWNED" -> "ZOMBIE_HEAD";
            case "SKELETON", "STRAY" -> "SKELETON_SKULL";
            case "WITHER_SKELETON" -> "WITHER_SKELETON_SKULL";
            case "CREEPER" -> "CREEPER_HEAD";
            case "ENDER_DRAGON" -> "DRAGON_HEAD";
            case "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN" -> "PIGLIN_HEAD";
            case "IRON_GOLEM" -> "IRON_BLOCK";
            case "SNOW_GOLEM" -> "CARVED_PUMPKIN";
            case "SLIME" -> "SLIME_BLOCK";
            case "MAGMA_CUBE" -> "MAGMA_BLOCK";
            case "SPIDER", "CAVE_SPIDER" -> "SPIDER_EYE";
            case "BLAZE" -> "BLAZE_POWDER";
            case "ENDERMAN" -> "ENDER_PEARL";
            case "ENDERMITE" -> "END_STONE";
            case "CHICKEN" -> "EGG";
            case "PIG" -> "PORKCHOP";
            case "COW" -> "BEEF";
            case "MOOSHROOM" -> "RED_MUSHROOM";
            case "SHEEP" -> "WHITE_WOOL";
            case "HORSE", "DONKEY", "MULE" -> "SADDLE";
            case "WOLF" -> "BONE";
            case "CAT", "OCELOT" -> "STRING";
            case "PARROT" -> "FEATHER";
            case "RABBIT" -> "RABBIT_FOOT";
            case "FOX" -> "SWEET_BERRIES";
            case "POLAR_BEAR" -> "SNOW_BLOCK";
            case "PANDA" -> "BAMBOO";
            case "BEE" -> "HONEYCOMB";
            case "TURTLE" -> "TURTLE_SCUTE";
            case "AXOLOTL" -> "TROPICAL_FISH";
            case "FROG" -> "LILY_PAD";
            case "WITCH" -> "POTION";
            case "VILLAGER", "WANDERING_TRADER" -> "EMERALD";
            case "VINDICATOR", "PILLAGER", "EVOKER" -> "CROSSBOW";
            case "RAVAGER" -> "SADDLE";
            case "VEX", "ALLAY" -> "ECHO_SHARD";
            case "GHAST" -> "GHAST_TEAR";
            case "PHANTOM" -> "PHANTOM_MEMBRANE";
            case "SHULKER" -> "SHULKER_SHELL";
            case "GUARDIAN", "ELDER_GUARDIAN" -> "PRISMARINE_SHARD";
            case "SQUID", "GLOW_SQUID" -> "INK_SAC";
            case "DOLPHIN" -> "COD";
            case "COD" -> "COD";
            case "SALMON" -> "SALMON";
            case "PUFFERFISH" -> "PUFFERFISH";
            case "TROPICAL_FISH" -> "TROPICAL_FISH";
            case "SILVERFISH" -> "STONE";
            case "BAT" -> "COAL";
            case "LLAMA", "TRADER_LLAMA" -> "LEATHER";
            case "CAMEL" -> "CACTUS";
            case "SNIFFER" -> "TORCHFLOWER_SEEDS";
            case "GOAT" -> "GOAT_HORN";
            case "HOGLIN", "ZOGLIN" -> "PORKCHOP";
            case "STRIDER" -> "WARPED_FUNGUS_ON_A_STICK";
            case "WITHER" -> "WITHER_SKELETON_SKULL";
            case "WARDEN" -> "SCULK_SHRIEKER";
            default -> "PAPER";
        };
    }
}
