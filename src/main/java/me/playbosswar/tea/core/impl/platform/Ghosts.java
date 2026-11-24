package me.playbosswar.tea.core.impl.platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;

/**
 * The modern platform also blocks events older versions don't have
 * (projectile hits, swap hands) for ghosts.
 */
public final class Ghosts {

    private static final List<Predicate<Entity>> CHECKS = new CopyOnWriteArrayList<>();

    private Ghosts() {
    }

    public static void register(Predicate<Entity> check) {
        CHECKS.add(check);
    }

    public static boolean isGhost(Entity entity) {
        if (entity == null) return false;
        for (Predicate<Entity> check : CHECKS) {
            if (check.test(entity)) return true;
        }
        return false;
    }
}
