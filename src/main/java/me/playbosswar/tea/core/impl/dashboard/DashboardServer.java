package me.playbosswar.tea.core.impl.dashboard;

import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardSessions;

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

public final class DashboardServer {

    static final long MAX_BODY = 64L * 1024;
    static final long MAX_UPLOAD_BODY = 300L * 1024 * 1024;
    static final String UPLOAD_PATH = "/api/maps-upload";

    private final CoreConfig.DashboardConfig config;
    private final Logger logger;
    private final TeaCorePlugin plugin;
    private final TeaCoreAPI core;

    private Javalin app;
    private DashboardEventBroadcaster broadcaster;
    private DashboardAuth auth;
    private BukkitTask queueTicker;
    private me.playbosswar.tea.core.impl.metrics.MetricsCollector metrics;

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
                // Javalin has no per-route limit; limitBody holds other routes to MAX_BODY.
                cfg.http.maxRequestSize = MAX_UPLOAD_BODY;
                cfg.jetty.defaultHost = config.bindAddress();
                cfg.jetty.defaultPort = config.port();
                // spaRoot makes client-side-routed URLs (e.g. /arenas) fall back to index.html
                // instead of 404.
                cfg.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/dashboard";
                    staticFiles.location = Location.CLASSPATH;
                    staticFiles.precompress = false;
                });
                cfg.spaRoot.addFile("/", "/dashboard/index.html", Location.CLASSPATH);
            });

            this.auth = new DashboardAuth(new DashboardSessions(), plugin.dashboardLoginCodes(),
                    config.adminToken(), logger);
            if (plugin.discordEvents() != null) {
                auth.setStaffSink(plugin.discordEvents()::staff);
            }
            javalin.before("/api/*", auth::authenticate);
            javalin.before(DashboardServer::limitBody);
            auth.register(javalin);

            // Prometheus: off until metrics.token is set. Separate from the admin token so a
            // leaked scrape config can't act on the network.
            this.metrics = new me.playbosswar.tea.core.impl.metrics.MetricsCollector(core.coordination());
            javalin.get("/metrics", ctx -> {
                String token = plugin.getConfig().getString("metrics.token", "");
                if (token == null || token.isBlank()) {
                    ctx.status(HttpStatus.NOT_FOUND).result("metrics are off (set metrics.token)");
                    return;
                }
                String header = ctx.header("Authorization");
                String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
                if (!java.security.MessageDigest.isEqual(given.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        token.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    ctx.status(HttpStatus.UNAUTHORIZED).result("unauthorized");
                    return;
                }
                ctx.contentType("text/plain; version=0.0.4; charset=utf-8").result(metrics.render());
            });

            javalin.get("/api/health", ctx -> ctx.json(Map.of(
                    "status", "ok",
                    "version", "dashboard-v1",
                    "sessions", broadcaster == null ? 0 : broadcaster.sessionCount())));

            this.broadcaster = new DashboardEventBroadcaster(core.coordination(), logger,
                    s -> s.uuid() == null && "token".equals(s.id()) || auth.sessions().get(s.token()).isPresent());
            new DashboardApi(core, plugin, auth, broadcaster).register(javalin);

            javalin.ws("/api/events", ws -> {
                ws.onConnect(ctx -> {
                    var session = auth.resolve(ctx.cookie(DashboardAuth.COOKIE), ctx.header("Authorization"));
                    if (session.isEmpty() || !session.get().can(DashboardPermission.VIEW)) {
                        ctx.closeSession(1008, "unauthorized");
                        return;
                    }
                    broadcaster.addSession(ctx, session.get());
                });
                ws.onClose(ctx -> broadcaster.removeSession(ctx));
                ws.onError(ctx -> broadcaster.removeSession(ctx));
            });

            javalin.start();
            this.app = javalin;
            logger.info(() -> "Dashboard API listening on " + config.bindAddress()
                    + ":" + config.port());

            this.queueTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::pushQueueCounts, 40L, 20L);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start dashboard HTTP server", e);
        }
    }

    /** Runs after authentication, so only a signed-in user who may upload maps gets the large limit. */
    static void limitBody(io.javalin.http.Context ctx) {
        long length = ctx.req().getContentLengthLong();
        boolean chunked = ctx.header("Transfer-Encoding") != null;
        if (length <= 0 && !chunked) {
            return;
        }
        if (length < 0) {
            ctx.status(HttpStatus.LENGTH_REQUIRED).json(Map.of("error", "Content-Length is required"));
            ctx.skipRemainingHandlers();
            return;
        }
        var session = DashboardAuth.session(ctx);
        boolean upload = ctx.path().equals(UPLOAD_PATH) && session != null
                && session.can(DashboardPermission.MAPS_TRANSFER);
        if (length > (upload ? MAX_UPLOAD_BODY : MAX_BODY)) {
            ctx.status(HttpStatus.CONTENT_TOO_LARGE).json(Map.of("error", "request body too large"));
            ctx.skipRemainingHandlers();
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
        if (broadcaster == null || broadcaster.sessionCount() == 0)
            return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GameDefinition def : core.games().all()) {
            counts.put(def.id().value(), core.coordination().queueFor(def.id()).size());
        }
        broadcaster.broadcastQueueCounts(counts);
    }
}
