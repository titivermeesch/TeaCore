package me.playbosswar.tea.core.api.loot;

import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** A weighted loot table parsed from config. Rolls are independent. */
public interface LootTable {

    /** The table id it was registered under. */
    String id();

    /**
     * Rolls a random number of entries between the table's min and max items,
     * each picked by weight.
     *
     * @return the rolled items; never {@code null}, may be empty.
     */
    List<ItemStack> roll();

    /**
     * Clears {@code inventory} and fills it with a fresh {@link #roll()},
     * spreading items over random empty slots.
     *
     * @param inventory the inventory to fill, e.g. a chest's.
     */
    void fill(Inventory inventory);
}
