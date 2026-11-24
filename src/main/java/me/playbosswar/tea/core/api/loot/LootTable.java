package me.playbosswar.tea.core.api.loot;

import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** A weighted loot table parsed from config. Rolls are independent. */
public interface LootTable {

    String id();

    /** Rolls between the table's min and max items, each picked by weight. May be empty. */
    List<ItemStack> roll();

    /** Clears {@code inventory} and spreads a fresh {@link #roll()} over random slots. */
    void fill(Inventory inventory);
}
