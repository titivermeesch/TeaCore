package me.playbosswar.tea.core.api.platform;

import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hidden string tags on items and entities, for marking "this is the kit item"
 * or "this fireball is a meteor". Keys are namespaced by the owning plugin.
 *
 * <p>Items keep their tags when saved and moved between inventories (persistent
 * data on 1.16.5+, item NBT on 1.8 - 1.12). On 1.8 - 1.12 entity tags only live
 * while the server runs; that's enough for projectiles and NPCs.
 */
public interface ItemTags {

    /** Tags and returns the same stack; no copy is made. */
    ItemStack set(ItemStack item, String plugin, String key, String value);

    @Nullable String get(@Nullable ItemStack item, String plugin, String key);

    default boolean has(@Nullable ItemStack item, String plugin, String key) {
        return get(item, plugin, key) != null;
    }

    ItemStack remove(ItemStack item, String plugin, String key);

    void set(Entity entity, String plugin, String key, String value);

    @Nullable String get(@Nullable Entity entity, String plugin, String key);

    default boolean has(@Nullable Entity entity, String plugin, String key) {
        return get(entity, plugin, key) != null;
    }
}
