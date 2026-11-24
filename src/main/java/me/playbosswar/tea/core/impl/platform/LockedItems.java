package me.playbosswar.tea.core.impl.platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/**
 * The modern platform cancels swap-hands for any match. Servers before 1.9
 * have no off hand.
 */
public final class LockedItems {

    private static final List<Predicate<ItemStack>> CHECKS = new CopyOnWriteArrayList<>();

    private LockedItems() {
    }

    public static void register(Predicate<ItemStack> check) {
        CHECKS.add(check);
    }

    public static boolean isLocked(ItemStack item) {
        if (item == null) return false;
        for (Predicate<ItemStack> check : CHECKS) {
            if (check.test(item)) return true;
        }
        return false;
    }
}
