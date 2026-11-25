package me.playbosswar.tea.core.impl.arena;

import java.lang.reflect.Method;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.platform.Platform;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Creates TeaCore's temporary worlds without preparing spawn chunks, which
 * otherwise holds the main thread long enough for Paper's watchdog to fire.
 *
 * <p>Paper 1.20+ skips them through {@code WorldCreator#keepSpawnLoaded}. Everywhere
 * else the server checks keep-spawn-in-memory (the {@code spawnChunkRadius} game
 * rule from 1.20.5) between {@link WorldInitEvent} and loading the spawn area, so
 * turning it off in that event skips the load.
 */
public final class ArenaWorlds {

    private static String creating;
    private static boolean listening;

    private ArenaWorlds() {
    }

    public static @Nullable World create(WorldCreator creator) {
        skipSpawnChunks(creator);
        listen();
        String previous = creating;
        creating = creator.name();
        World world;
        try {
            world = creator.createWorld();
        } finally {
            creating = previous;
        }
        if (world == null) {
            return null;
        }
        disableSpawnChunks(world);
        return world;
    }

    private static void disableSpawnChunks(World world) {
        try {
            world.setKeepSpawnInMemory(false);
        } catch (UnsupportedOperationException | NoSuchMethodError ignored) {
            // Removed or unsupported on this server; the game rule below covers it.
        }
        Platform platform = TeaCoreAPI.get().platform();
        if (!platform.version().legacy()) {
            platform.worlds().setGameRule(world, "spawnChunkRadius", "0");
        }
    }

    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onInit(WorldInitEvent event) {
                if (event.getWorld().getName().equals(creating)) {
                    disableSpawnChunks(event.getWorld());
                }
            }
        }, JavaPlugin.getProvidingPlugin(ArenaWorlds.class));
    }

    /** Paper 1.20+: {@code WorldCreator#keepSpawnLoaded(TriState.FALSE)}, which the 1.8.8 API lacks. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void skipSpawnChunks(WorldCreator creator) {
        for (Method m : creator.getClass().getMethods()) {
            if (m.getName().equals("keepSpawnLoaded") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isEnum()) {
                try {
                    m.invoke(creator, Enum.valueOf((Class) m.getParameterTypes()[0], "FALSE"));
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Different signature than expected: create the world the normal way.
                }
                return;
            }
        }
    }
}
