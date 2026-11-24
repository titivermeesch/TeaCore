package me.playbosswar.tea.core.impl.platform;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.profiles.builder.XSkull;
import com.cryptomorin.xseries.profiles.objects.Profileable;
import java.util.Locale;
import java.util.Optional;
import me.playbosswar.tea.core.api.platform.Items;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class XItems implements Items {

    private final java.util.function.BiFunction<String, Integer, ItemStack> potions;

    /** @param potions builds a potion item for this version (type name, amount) */
    public XItems(java.util.function.BiFunction<String, Integer, ItemStack> potions) {
        this.potions = potions;
    }

    @Override
    public org.bukkit.enchantments.Enchantment enchantment(String modernName) {
        return com.cryptomorin.xseries.XEnchantment.of(modernName.trim().toUpperCase(Locale.ROOT))
                .filter(com.cryptomorin.xseries.XEnchantment::isSupported)
                .map(com.cryptomorin.xseries.XEnchantment::get).orElse(null);
    }

    private static final java.lang.reflect.Method CUSTOM_MODEL_DATA = cmd();

    private static java.lang.reflect.Method cmd() {
        try {
            return org.bukkit.inventory.meta.ItemMeta.class.getMethod("setCustomModelData", Integer.class);
        } catch (NoSuchMethodException e) {
            return null; // before 1.14
        }
    }

    @Override
    public void setCustomModelData(org.bukkit.inventory.meta.ItemMeta meta, int value) {
        if (CUSTOM_MODEL_DATA != null) {
            try {
                CUSTOM_MODEL_DATA.invoke(meta, value);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @Override
    public void setUnbreakable(org.bukkit.inventory.meta.ItemMeta meta, boolean unbreakable) {
        try {
            org.bukkit.inventory.meta.ItemMeta.class.getMethod("setUnbreakable", boolean.class).invoke(meta, unbreakable);
            return;
        } catch (ReflectiveOperationException e) {
            // before 1.11: through meta.spigot()
        }
        try {
            Object spigot = org.bukkit.inventory.meta.ItemMeta.class.getMethod("spigot").invoke(meta);
            Class.forName("org.bukkit.inventory.meta.ItemMeta$Spigot")
                    .getMethod("setUnbreakable", boolean.class).invoke(spigot, unbreakable);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public ItemStack potion(String type, int amount) {
        ItemStack item = potions.apply(type.toUpperCase(Locale.ROOT), Math.max(1, amount));
        return item != null ? item : stackOr("POTION", "GLASS_BOTTLE", amount);
    }

    private static Optional<XMaterial> x(String name) {
        return name == null ? Optional.empty() : XMaterial.matchXMaterial(name.toUpperCase(Locale.ROOT))
                .filter(XMaterial::isSupported);
    }

    @Override
    public Optional<ItemStack> stack(String modernName, int amount) {
        return x(modernName).map(m -> {
            ItemStack item = m.parseItem();
            if (item != null) {
                item.setAmount(Math.max(1, amount));
            }
            return item;
        });
    }

    @Override
    public ItemStack stackOr(String modernName, String fallback, int amount) {
        return stack(modernName, amount).or(() -> stack(fallback, amount))
                .orElseGet(() -> new ItemStack(Material.STONE, Math.max(1, amount)));
    }

    @Override
    public Optional<Material> material(String modernName) {
        return x(modernName).map(XMaterial::get);
    }

    @Override
    public boolean is(ItemStack item, String modernName) {
        return item != null && x(modernName).map(m -> m.isSimilar(item)).orElse(false);
    }

    @Override
    public String nameOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            return XMaterial.matchXMaterial(item).name();
        } catch (IllegalArgumentException e) {
            return item.getType().name();
        }
    }

    @Override
    public ItemStack colored(String color, String kind, int amount) {
        String name = color.toUpperCase(Locale.ROOT) + "_" + kind.toUpperCase(Locale.ROOT);
        return stackOr(name, "WHITE_" + kind.toUpperCase(Locale.ROOT), amount);
    }

    @Override
    public ItemStack head(String textureBase64) {
        try {
            return XSkull.createItem().profile(Profileable.detect(textureBase64)).apply();
        } catch (RuntimeException e) {
            return stackOr("PLAYER_HEAD", "STONE", 1);
        }
    }
}
