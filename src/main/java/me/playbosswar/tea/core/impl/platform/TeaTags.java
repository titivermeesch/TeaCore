package me.playbosswar.tea.core.impl.platform;

import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.platform.ItemTags;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public final class TeaTags {

    private static final String OWNER = "teacore";

    private TeaTags() {
    }

    private static ItemTags tags() {
        return TeaCoreAPI.get().platform().tags();
    }

    /** Use the returned stack. */
    public static ItemStack mark(ItemStack item, String key) {
        return tags().set(item, OWNER, key, "1");
    }

    public static ItemStack set(ItemStack item, String key, String value) {
        return tags().set(item, OWNER, key, value);
    }

    public static boolean marked(ItemStack item, String key) {
        return item != null && tags().has(item, OWNER, key);
    }

    public static String get(ItemStack item, String key) {
        return tags().get(item, OWNER, key);
    }

    public static void mark(Entity entity, String key) {
        tags().set(entity, OWNER, key, "1");
    }

    public static void set(Entity entity, String key, String value) {
        tags().set(entity, OWNER, key, value);
    }

    public static boolean marked(Entity entity, String key) {
        return entity != null && tags().has(entity, OWNER, key);
    }

    public static String get(Entity entity, String key) {
        return tags().get(entity, OWNER, key);
    }
}
