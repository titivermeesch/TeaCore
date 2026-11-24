package me.playbosswar.tea.core.impl.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ServerStatsPublisher {

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final IntSupplier spareWorlds;
    private BukkitTask task;

    public ServerStatsPublisher(Plugin plugin, CoordinationBackend backend, IntSupplier spareWorlds) {
        this.plugin = plugin;
        this.backend = backend;
        this.spareWorlds = spareWorlds;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::publish, 100L, 300L);
        lastPublish = System.nanoTime();
        ticksSince = 0;
        tickCounter = Bukkit.getScheduler().runTaskTimer(plugin, () -> ticksSince++, 1L, 1L);
    }

    // Spigot has no TPS API: count ticks between publishes instead.
    private BukkitTask tickCounter;
    private long lastPublish;
    private long ticksSince;

    private double measuredTps() {
        long now = System.nanoTime();
        double seconds = (now - lastPublish) / 1e9;
        double tps = seconds <= 0 ? 20.0 : ticksSince / seconds;
        lastPublish = now;
        ticksSince = 0;
        return tps;
    }

    /** Paper's 1-minute TPS, or -1 on Spigot. */
    private static double paperTps() {
        try {
            Object v = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            return v instanceof double[] a && a.length > 0 ? a[0] : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Paper's average tick time in ms, or -1 on Spigot. */
    private static double paperMspt() {
        try {
            Object v = Bukkit.getServer().getClass().getMethod("getAverageTickTime").invoke(Bukkit.getServer());
            return v instanceof Number n ? n.doubleValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (tickCounter != null) {
            tickCounter.cancel();
            tickCounter = null;
        }
    }

    /** Must run on the main thread. */
    public void publish() {
        Runtime rt = Runtime.getRuntime();
        Map<String, String> data = new HashMap<>();
        double tps = paperTps();
        data.put("tps", String.valueOf(Math.min(20.0, tps >= 0 ? tps : measuredTps())));
        double mspt = paperMspt();
        if (mspt >= 0) {
            data.put("mspt", String.valueOf(mspt));
        }
        data.put("players", String.valueOf(Bukkit.getOnlinePlayers().size()));
        data.put("memoryUsed", String.valueOf(rt.totalMemory() - rt.freeMemory()));
        data.put("memoryMax", String.valueOf(rt.maxMemory()));
        data.put("spareWorlds", String.valueOf(spareWorlds.getAsInt()));
        int players = Bukkit.getOnlinePlayers().size();
        try {
            backend.publishEvent(new CoordinationEvent(CoordinationEvent.Type.SERVER_STATS, backend.serverId(),
                    null, null, null, null, null, null, null, null, null, players, -1, data));
        } catch (RuntimeException ignored) {
            // backend down: try again next time
        }
    }
}
