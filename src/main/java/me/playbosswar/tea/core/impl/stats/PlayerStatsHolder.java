package me.playbosswar.tea.core.impl.stats;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker holder so the click listener can identify the {@code /stats} GUI
 * (vs. some other inventory) and disable shift-click / drag interactions
 * inside it.
 */
public final class PlayerStatsHolder implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
