package me.playbosswar.tea.core.api.loot;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Builds a game's custom items for loot tables. Registered under a namespace
 * (e.g. {@code fireball}); table entries then reference items as
 * {@code item: "fireball:explosive"} or the shorthand {@code fireball: explosive}.
 */
@FunctionalInterface
public interface LootItemProvider {

    /**
     * Creates the item with the given id.
     *
     * @param id     the part after the namespace, e.g. {@code explosive}.
     * @param amount the rolled stack size (at least 1).
     * @return the item, or empty if the id is unknown.
     */
    Optional<ItemStack> create(String id, int amount);
}
