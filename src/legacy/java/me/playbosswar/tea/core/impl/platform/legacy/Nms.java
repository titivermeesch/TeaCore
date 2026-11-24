package me.playbosswar.tea.core.impl.platform.legacy;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * The bit of 1.8-1.12 server internals TeaCore needs: string tags in item NBT
 * (under a "tea" compound) and byte flags in entity NBT. Method names are the
 * 1.8 (v1_8_R3) obfuscated ones: Entity#e(tag) saves, Entity#f(tag) loads.
 */
final class Nms {

    private static final String TEA = "tea";
    private final Class<?> craftItemStack;
    private final Class<?> nbtCompound;
    private final Method asNmsCopy;
    private final Method asBukkitCopy;
    private final Method getTag;
    private final Method setTag;
    private final Method hasKey;
    private final Method getString;
    private final Method setString;
    private final Method getCompound;
    private final Method setCompound;
    private final Method removeKey;
    private final Method setByte;
    private final Method entityHandle;
    private final Method entitySave;
    private final Method entityLoad;
    private final Method worldHandle;
    private final Method createExplosion;

    Nms() {
        try {
            String obc = Bukkit.getServer().getClass().getPackage().getName();
            String nmsPkg = "net.minecraft.server." + obc.substring(obc.lastIndexOf('.') + 1);
            craftItemStack = Class.forName(obc + ".inventory.CraftItemStack");
            Class<?> nmsItem = Class.forName(nmsPkg + ".ItemStack");
            nbtCompound = Class.forName(nmsPkg + ".NBTTagCompound");
            Class<?> nbtBase = Class.forName(nmsPkg + ".NBTBase");
            Class<?> nmsEntity = Class.forName(nmsPkg + ".Entity");
            asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsItem);
            getTag = nmsItem.getMethod("getTag");
            setTag = nmsItem.getMethod("setTag", nbtCompound);
            hasKey = nbtCompound.getMethod("hasKey", String.class);
            getString = nbtCompound.getMethod("getString", String.class);
            setString = nbtCompound.getMethod("setString", String.class, String.class);
            getCompound = nbtCompound.getMethod("getCompound", String.class);
            setCompound = nbtCompound.getMethod("set", String.class, nbtBase);
            removeKey = nbtCompound.getMethod("remove", String.class);
            setByte = nbtCompound.getMethod("setByte", String.class, byte.class);
            entityHandle = Class.forName(obc + ".entity.CraftEntity").getMethod("getHandle");
            entitySave = nmsEntity.getMethod("e", nbtCompound);
            entityLoad = nmsEntity.getMethod("f", nbtCompound);
            worldHandle = Class.forName(obc + ".CraftWorld").getMethod("getHandle");
            createExplosion = Class.forName(nmsPkg + ".World").getMethod("createExplosion", nmsEntity,
                    double.class, double.class, double.class, float.class, boolean.class, boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported server internals for the legacy platform", e);
        }
    }

    private Object tea(Object nmsItem, boolean create) throws ReflectiveOperationException {
        Object tag = getTag.invoke(nmsItem);
        if (tag == null) {
            if (!create) return null;
            tag = nbtCompound.getConstructor().newInstance();
            setTag.invoke(nmsItem, tag);
        }
        if (!(boolean) hasKey.invoke(tag, TEA)) {
            if (!create) return null;
            setCompound.invoke(tag, TEA, nbtCompound.getConstructor().newInstance());
        }
        return getCompound.invoke(tag, TEA);
    }

    ItemStack setItemTag(ItemStack item, String key, String value) {
        try {
            Object nms = asNmsCopy.invoke(null, item);
            if (nms == null) return item;
            setString.invoke(tea(nms, true), key, value);
            ItemStack out = (ItemStack) asBukkitCopy.invoke(null, nms);
            item.setItemMeta(out.getItemMeta());
            return item;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    String getItemTag(ItemStack item, String key) {
        try {
            Object nms = asNmsCopy.invoke(null, item);
            if (nms == null) return null;
            Object tea = tea(nms, false);
            return tea != null && (boolean) hasKey.invoke(tea, key) ? (String) getString.invoke(tea, key) : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    ItemStack removeItemTag(ItemStack item, String key) {
        try {
            Object nms = asNmsCopy.invoke(null, item);
            if (nms == null) return item;
            Object tea = tea(nms, false);
            if (tea != null) {
                removeKey.invoke(tea, key);
                item.setItemMeta(((ItemStack) asBukkitCopy.invoke(null, nms)).getItemMeta());
            }
            return item;
        } catch (ReflectiveOperationException e) {
            return item;
        }
    }

    // 1.8 - 1.12 attribute internals, looked up by attribute name.

    private final java.util.Map<String, Object> attributes = new java.util.HashMap<>();

    private Object attributeInstance(org.bukkit.entity.LivingEntity entity, String modernName)
            throws ReflectiveOperationException {
        String key = switch (modernName.toUpperCase(java.util.Locale.ROOT)) {
            case "MAX_HEALTH" -> "generic.maxHealth";
            case "MOVEMENT_SPEED" -> "generic.movementSpeed";
            case "KNOCKBACK_RESISTANCE" -> "generic.knockbackResistance";
            case "ATTACK_DAMAGE" -> "generic.attackDamage";
            case "FOLLOW_RANGE" -> "generic.followRange";
            default -> null;
        };
        if (key == null) {
            return null;
        }
        Object handle = entityHandle.invoke(entity);
        Object type = attributes.get(key);
        if (type == null) {
            Class<?> generic = Class.forName(handle.getClass().getPackage().getName() + ".GenericAttributes");
            for (java.lang.reflect.Field f : generic.getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Object candidate = f.get(null);
                if (candidate != null && key.equals(candidate.getClass().getMethod("getName").invoke(candidate))) {
                    type = candidate;
                    attributes.put(key, type);
                    break;
                }
            }
            if (type == null) {
                return null;
            }
        }
        Class<?> iAttribute = Class.forName(handle.getClass().getPackage().getName() + ".IAttribute");
        Method get = findMethod(handle.getClass(), "getAttributeInstance", iAttribute);
        return get == null ? null : get.invoke(handle, type);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, args);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // look in the superclass
            }
        }
        return null;
    }

    double attribute(org.bukkit.entity.LivingEntity entity, String modernName) {
        try {
            Object instance = attributeInstance(entity, modernName);
            return instance == null ? Double.NaN
                    : (double) instance.getClass().getMethod("getValue").invoke(instance);
        } catch (ReflectiveOperationException e) {
            return Double.NaN;
        }
    }

    void setAttributeBase(org.bukkit.entity.LivingEntity entity, String modernName, double value) {
        try {
            Object instance = attributeInstance(entity, modernName);
            if (instance != null) {
                instance.getClass().getMethod("setValue", double.class).invoke(instance, value);
            }
        } catch (ReflectiveOperationException ignored) {
            // leave it
        }
    }

    /** World#createExplosion with a source entity (the Bukkit API gained that in 1.14). */
    void explode(org.bukkit.Location at, float power, boolean fire, boolean breakBlocks, Entity source) {
        try {
            Object world = worldHandle.invoke(at.getWorld());
            Object handle = source == null ? null : entityHandle.invoke(source);
            createExplosion.invoke(world, handle, at.getX(), at.getY(), at.getZ(), power, fire, breakBlocks);
        } catch (ReflectiveOperationException e) {
            at.getWorld().createExplosion(at.getX(), at.getY(), at.getZ(), power, fire, breakBlocks);
        }
    }

    void setEntityByte(Entity entity, String key, int value) {
        try {
            Object handle = entityHandle.invoke(entity);
            Object tag = nbtCompound.getConstructor().newInstance();
            entitySave.invoke(handle, tag);
            setByte.invoke(tag, key, (byte) value);
            entityLoad.invoke(handle, tag);
        } catch (ReflectiveOperationException e) {
            // best effort: the NPC code also anchors and protects entities itself
        }
    }
}
