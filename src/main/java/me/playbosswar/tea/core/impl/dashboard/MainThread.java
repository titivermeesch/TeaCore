package me.playbosswar.tea.core.impl.dashboard;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Javalin serves requests from a Jetty worker pool, but Bukkit calls must run on the
 * main thread. {@link #runSync(Plugin, Callable)} wraps {@code callSyncMethod} with a
 * timeout and unwraps checked exceptions so the handler can rethrow them.
 */
final class MainThread {

    private static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;

    private MainThread() {
    }

    static <T> T runSync(Plugin plugin, Callable<T> task) {
        return runSync(plugin, task, DEFAULT_TIMEOUT_MILLIS);
    }

    static <T> T runSync(Plugin plugin, Callable<T> task, long timeoutMillis) {
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, task);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting on main-thread task", e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Main-thread task timed out after " + timeoutMillis + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
