package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Embedded HTTP server + WebSocket broadcaster for the TeaCore management
 * dashboard.
 *
 * <p>
 * Javalin (Jetty under the hood) hosts every {@code /api/**} route exposed by
 * {@link DashboardApi} and a single WebSocket at {@code /api/events}. Every
 * HTTP and WebSocket handshake is gated by the admin token configured under
 * {@code dashboard.admin-token}; clients pass it as
 * {@code Authorization: Bearer <token>} or as a {@code ?token=<token>} query
 * param (the browser cannot set custom headers on the WebSocket upgrade, so
 * query-param auth is the intended mechanism for the live feed).
 */
public final class DashboardServer {

    private final CoreConfig.DashboardConfig config;
    private final Logger logger;
    private final TeaCorePlugin plugin;
    private final TeaCoreAPI core;

    private Javalin app;
    private DashboardEventBroadcaster broadcaster;
    private BukkitTask queueTicker;

    public DashboardServer(TeaCorePlugin plugin, TeaCoreAPI core,
                           CoreConfig.DashboardConfig config, Logger logger) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.logger = logger;
    }

    public void start() {
        if (!config.enabled()) {
            logger.info("Dashboard disabled in config; skipping HTTP server startup.");
            return;
        }
        try {
            Javalin javalin = Javalin.create(cfg -> {
                cfg.showJavalinBanner = false;
                cfg.jetty.defaultHost = config.bindAddress();
                cfg.jetty.defaultPort = config.port();
                // Serve the Vite-built SPA from /dashboard/ on the plugin jar's
                // classpath. spaRoot makes client-side-routed URLs (e.g.
                // /arenas) fall back to index.html instead of 404ing.
                cfg.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/dashboard";
                    staticFiles.location = Location.CLASSPATH;
                    staticFiles.precompress = false;
                });
                cfg.spaRoot.addFile("/", "/dashboard/index.html", Location.CLASSPATH);
            });

            javalin.before("/api/*", ctx -> {
                if (!authorized(ctx.header("Authorization"), ctx.queryParam("token"))) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
                    ctx.skipRemainingHandlers();
                }
            });

            javalin.get("/api/health", ctx -> ctx.json(Map.of(
                    "status", "ok",
                    "version", "dashboard-v1",
                    "sessions", broadcaster == null ? 0 : broadcaster.sessionCount())));

            new DashboardApi(core, plugin).register(javalin);

            this.broadcaster = new DashboardEventBroadcaster(core.coordination(), logger);

            javalin.ws("/api/events", ws -> {
                ws.onConnect(ctx -> {
                    if (!authorized(null, ctx.queryParam("token"))) {
                        ctx.closeSession(1008, "unauthorized");
                        return;
                    }
                    broadcaster.addSession(ctx);
                });
                ws.onClose(ctx -> broadcaster.removeSession(ctx));
                ws.onError(ctx -> broadcaster.removeSession(ctx));
            });

            javalin.start();
            this.app = javalin;
            logger.info(() -> "Dashboard API listening on " + config.bindAddress()
                    + ":" + config.port() + " (admin token configured)");

            this.queueTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::pushQueueCounts, 40L, 20L);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start dashboard HTTP server", e);
        }
    }

    public void stop() {
        if (queueTicker != null) {
            try {
                queueTicker.cancel();
            } catch (Exception ignored) {
            }
            queueTicker = null;
        }
        if (app != null) {
            try {
                app.stop();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error stopping dashboard HTTP server", e);
            }
            app = null;
        }
        broadcaster = null;
    }

    private void pushQueueCounts() {
        if (broadcaster == null || broadcaster.sessionCount() == 0) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GameDefinition def : core.games().all()) {
            counts.put(def.id().value(), core.coordination().queueFor(def.id()).size());
        }
        broadcaster.broadcastQueueCounts(counts);
    }

    private boolean authorized(String authHeader, String tokenQuery) {
        String expected = config.adminToken();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String candidate = authHeader.substring("Bearer ".length()).trim();
            if (constantTimeEquals(candidate, expected)) return true;
        }
        if (tokenQuery != null && constantTimeEquals(tokenQuery, expected)) {
            return true;
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
