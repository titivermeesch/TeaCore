package me.playbosswar.tea.core.impl.dashboard;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Helpers for marshaling Javalin worker-thread handlers onto the Bukkit main
 * thread.
 *
 * <p>
 * Every Bukkit API call (world creation, player teleport, event dispatch)
 * must run on the main thread; Javalin serves HTTP requests from a Jetty
 * worker pool, so control-plane endpoints that mutate arenas or maps have to
 * hop threads before touching anything. {@link #runSync(Plugin, Callable)}
 * wraps {@code Bukkit.getScheduler().callSyncMethod} with a timeout and
 * unwraps checked exceptions into unchecked form the handler can rethrow.
 */
final class MainThread {

    private static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;

    private MainThread() {
    }

    static <T> T runSync(Plugin plugin, Callable<T> task) {
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, task);
        try {
            return future.get(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting on main-thread task", e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Main-thread task timed out after "
                    + DEFAULT_TIMEOUT_MILLIS + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
