package me.playbosswar.tea.core.impl.kit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Builds + opens an inventory-based kit selector for a player and game. */
public final class KitSelectorGui {

    private static final int SIZE = 27; // 3 rows
    private static final int FIRST_SLOT = 10; // start of row 2, leaving edges empty
    private static final int LAST_SLOT = 16;

    private final Plugin plugin;
    private final KitRegistry kitRegistry;
    private final MessageService messageService;
    private final NamespacedKey kitKey;

    public KitSelectorGui(Plugin plugin, KitRegistry kitRegistry, MessageService messageService) {
        this.plugin = plugin;
        this.kitRegistry = kitRegistry;
        this.messageService = messageService;
        this.kitKey = new NamespacedKey(plugin, "kit_id");
    }

    public NamespacedKey kitKey() {
        return kitKey;
    }

    @SuppressWarnings("deprecation")
    public boolean open(Player player, GameId gameId) {
        Collection<Kit> kits = kitRegistry.kitsFor(gameId);
        if (kits.isEmpty()) {
            messageService.send(player, "core.kit-picker.none", Map.of("game", gameId.value()));
            return false;
        }
        String locale = messageService.resolveLocale(player);
        String title = messageService.renderLegacyNoPrefix(locale, "core.kit-picker.title",
                Map.of("game", gameId.value()));

        KitSelectorHolder holder = new KitSelectorHolder(gameId);
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        Kit current = kitRegistry.selectedKit(player, gameId).orElse(null);
        int slot = FIRST_SLOT;
        for (Kit kit : kits) {
            if (slot > LAST_SLOT)
                break;
            inv.setItem(slot++, buildKitItem(kit, current, locale));
        }

        player.openInventory(inv);
        return true;
    }

    private ItemStack buildKitItem(Kit kit, Kit currentSelection, String locale) {
        Material material = kit.icon() == null ? Material.PAPER : kit.icon();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null)
            return stack;

        String name = messageService.renderLegacyNoPrefix(locale, kit.displayNameKey(), Map.of());
        String desc = messageService.renderLegacyNoPrefix(locale, kit.descriptionKey(), Map.of());
        meta.setDisplayName(name);
        meta.setLore(List.of(desc));
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.kitId());

        if (currentSelection != null && currentSelection.kitId().equals(kit.kitId())) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        stack.setItemMeta(meta);
        return stack;
    }
}
