package me.playbosswar.tea.core.impl.kit;

import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker holder so the click listener can identify kit-selector inventories and
 * which game they are for.
 */
public final class KitSelectorHolder implements InventoryHolder {

    private final GameId gameId;
    private Inventory inventory;

    public KitSelectorHolder(GameId gameId) {
        this.gameId = gameId;
    }

    public GameId gameId() {
        return gameId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
