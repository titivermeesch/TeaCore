package me.playbosswar.tea.core.impl.platform;

import org.bukkit.enchantments.Enchantment;

public final class Compat {

    private Compat() {
    }

    /** UNBREAKING, called DURABILITY before 1.20.5. */
    @SuppressWarnings("deprecation")
    public static Enchantment glintEnchantment() {
        Enchantment e = Enchantment.getByName("UNBREAKING");
        return e != null ? e : Enchantment.getByName("DURABILITY");
    }
}
