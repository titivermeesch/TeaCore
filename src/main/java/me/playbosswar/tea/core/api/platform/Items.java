package me.playbosswar.tea.core.api.platform;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Items by their modern (1.13+) names, e.g. {@code RED_WOOL} or
 * {@code FIRE_CHARGE}. On older servers they become the matching legacy item
 * and data value (WOOL:14, FIREBALL).
 */
public interface Items {

    /** A stack of the item, or empty if this version has nothing like it. */
    Optional<ItemStack> stack(String modernName, int amount);

    /** Like {@link #stack} but falls back to {@code fallback} (also a modern name), then STONE. */
    ItemStack stackOr(String modernName, String fallback, int amount);

    /** The material (without data value), or empty. */
    Optional<Material> material(String modernName);

    /** Whether the stack is that item (data value included on old servers). */
    boolean is(@Nullable ItemStack item, String modernName);

    /** The modern name of what the stack is, e.g. RED_WOOL. */
    @Nullable String nameOf(@Nullable ItemStack item);

    /** Coloured wool / glass / dye etc. for a colour name such as "red": {@code colored("red", "WOOL")}. */
    ItemStack colored(String color, String kind, int amount);

    /**
     * A potion of the given type (PotionType name such as SPEED, STRONG_HEALING),
     * or a plain potion if the type is unknown on this version.
     */
    ItemStack potion(String type, int amount);

    /** An enchantment by modern name (SHARPNESS, UNBREAKING, ...), or null. */
    @Nullable org.bukkit.enchantments.Enchantment enchantment(String modernName);

    /** Sets custom model data (1.14+); does nothing on older versions. */
    void setCustomModelData(org.bukkit.inventory.meta.ItemMeta meta, int value);

    /** Makes the item unbreakable (ItemMeta on 1.11+, the Spigot meta before). */
    void setUnbreakable(org.bukkit.inventory.meta.ItemMeta meta, boolean unbreakable);

    /** A player head with the given skin texture (base64 "textures" value). */
    ItemStack head(String textureBase64);
}
