package me.playbosswar.tea.core.impl.platform.modern;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Bukkit calls whose signature changed after 1.16.5, looked up once by reflection
 * so the modern platform links on every 1.16.5+ server.
 */
final class OldApis {

    private OldApis() {
    }

    // World#spawn with an initializer: java.util.function.Consumer since 1.20.2,
    // org.bukkit.util.Consumer before.
    private static final Method SPAWN_JAVA = method(World.class, "spawn", Location.class, Class.class,
            java.util.function.Consumer.class);
    private static final Class<?> BUKKIT_CONSUMER = type("org.bukkit.util.Consumer");
    private static final Method SPAWN_BUKKIT = BUKKIT_CONSUMER == null ? null
            : method(World.class, "spawn", Location.class, Class.class, BUKKIT_CONSUMER);

    static <T extends Entity> T spawn(World world, Location at, Class<T> type, java.util.function.Consumer<? super T> init) {
        try {
            if (SPAWN_JAVA != null) {
                return type.cast(SPAWN_JAVA.invoke(world, at, type, init));
            }
            if (SPAWN_BUKKIT != null) {
                Object consumer = Proxy.newProxyInstance(OldApis.class.getClassLoader(), new Class<?>[] {BUKKIT_CONSUMER},
                        (proxy, m, args) -> {
                            if (m.getName().equals("accept") && args != null && args.length == 1) {
                                init.accept(type.cast(args[0]));
                                return null;
                            }
                            return switch (m.getName()) {
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                case "toString" -> "TeaCore spawn initializer";
                                default -> null;
                            };
                        });
                return type.cast(SPAWN_BUKKIT.invoke(world, at, type, consumer));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not spawn " + type.getSimpleName(), e);
        }
        throw new IllegalStateException("No World#spawn with an initializer");
    }

    // Potions: PotionMeta#setBasePotionType and the STRONG_/LONG_ types since 1.20.2,
    // PotionData before. The base types were renamed in 1.20.5.
    private static final Method SET_BASE_TYPE = method(PotionMeta.class, "setBasePotionType", PotionType.class);
    private static final Class<?> POTION_DATA = type("org.bukkit.potion.PotionData");
    private static final Method SET_BASE_DATA = POTION_DATA == null ? null
            : method(PotionMeta.class, "setBasePotionData", POTION_DATA);

    /** Whether the type was applied. */
    static boolean setPotion(PotionMeta meta, String type) {
        String prefix = type.startsWith("STRONG_") ? "STRONG_" : type.startsWith("LONG_") ? "LONG_" : "";
        String base = type.substring(prefix.length());
        try {
            if (SET_BASE_TYPE != null) {
                PotionType t = potionType(type, prefix + oldName(base), prefix + newName(base));
                if (t == null) return false;
                SET_BASE_TYPE.invoke(meta, t);
                return true;
            }
            if (SET_BASE_DATA != null) {
                PotionType t = potionType(base, oldName(base), newName(base));
                if (t == null) return false;
                Object data = POTION_DATA.getConstructor(PotionType.class, boolean.class, boolean.class)
                        .newInstance(t, prefix.equals("LONG_"), prefix.equals("STRONG_"));
                SET_BASE_DATA.invoke(meta, data);
                return true;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
        return false;
    }

    private static PotionType potionType(String... names) {
        for (String name : names) {
            try {
                return PotionType.valueOf(name);
            } catch (IllegalArgumentException notOnThisVersion) {
                // try the next name
            }
        }
        return null;
    }

    private static String oldName(String base) {
        return switch (base) {
            case "HEALING" -> "INSTANT_HEAL";
            case "HARMING" -> "INSTANT_DAMAGE";
            case "SWIFTNESS" -> "SPEED";
            case "LEAPING" -> "JUMP";
            case "REGENERATION" -> "REGEN";
            default -> base;
        };
    }

    private static String newName(String base) {
        return switch (base) {
            case "INSTANT_HEAL" -> "HEALING";
            case "INSTANT_DAMAGE" -> "HARMING";
            case "SPEED" -> "SWIFTNESS";
            case "JUMP" -> "LEAPING";
            case "REGEN" -> "REGENERATION";
            default -> base;
        };
    }

    private static final Class<?> CRITERIA = type("org.bukkit.scoreboard.Criteria");

    /** org.bukkit.scoreboard.Criteria arrived in 1.19.2. */
    static boolean hasCriteria() {
        return CRITERIA != null;
    }

    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... args) {
        if (owner == null) return null;
        try {
            return owner.getMethod(name, args);
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }
}
