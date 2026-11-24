package me.playbosswar.tea.core.api.platform;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Player API that changed between versions. */
public interface Players {

    /** The client's language, e.g. "en_us". */
    String locale(Player player);

    /** The item in the main hand (the only hand before 1.9). */
    @Nullable ItemStack mainHand(Player player);

    void setMainHand(Player player, @Nullable ItemStack item);

    /** The 36 main inventory slots (hotbar + storage), without armor or off hand. */
    ItemStack[] storage(Player player);

    void setStorage(Player player, ItemStack[] items);

    /** Whether the interact event is for the main hand (always true before 1.9). */
    boolean isMainHand(PlayerInteractEvent event);

    /** Whether the entity click is for the main hand (always true before 1.9). */
    boolean isMainHand(org.bukkit.event.player.PlayerInteractEntityEvent event);

    void setVisible(Player viewer, Player target, boolean visible);

    /** The block the player looks at within {@code range}, or {@code null}. */
    @Nullable org.bukkit.block.Block targetBlock(Player player, int range);

    /**
     * Shows the cooldown sweep over every hotbar item of this material. No-op
     * before 1.11.2.
     */
    default void showItemCooldown(Player player, org.bukkit.Material material, int ticks) {
        try {
            org.bukkit.entity.HumanEntity.class.getMethod("setCooldown", org.bukkit.Material.class, int.class)
                    .invoke(player, material, ticks);
        } catch (ReflectiveOperationException ignored) {
            // before 1.11.2
        }
    }
}
