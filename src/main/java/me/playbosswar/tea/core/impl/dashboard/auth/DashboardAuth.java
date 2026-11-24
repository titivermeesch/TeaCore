package me.playbosswar.tea.core.impl.dashboard.auth;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.SameSite;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Browsers sign in with a one-time code (or the admin token) and get a session
 * cookie. Scripts may send the admin token as {@code Authorization: Bearer <token>}.
 */
public final class DashboardAuth {

    public static final String COOKIE = "tea_session";
    private static final String ATTR = "tea.dashboard.session";
    /**
     * Counts only failed sign-ins per address, so a reverse proxy (one address for
     * everyone) can't be used to lock out people who type their code correctly.
     */
    private static final int MAX_FAILURES_PER_MINUTE = 30;

    private final DashboardSessions sessions;
    private final LoginCodes codes;
    private final String adminToken;
    private final Logger logger;
    private final Map<String, int[]> attempts = new ConcurrentHashMap<>();
    private volatile long attemptsMinute;

    public DashboardAuth(DashboardSessions sessions, LoginCodes codes, String adminToken, Logger logger) {
        this.sessions = sessions;
        this.codes = codes;
        this.adminToken = adminToken == null ? "" : adminToken;
        this.logger = logger;
    }

    public DashboardSessions sessions() {
        return sessions;
    }

    public Optional<DashboardSessions.Session> resolve(String cookie, String authHeader) {
        Optional<DashboardSessions.Session> s = sessions.get(cookie);
        if (s.isPresent()) {
            return s;
        }
        if (authHeader != null && authHeader.startsWith("Bearer ") && !adminToken.isEmpty()
                && constantTimeEquals(authHeader.substring(7).trim(), adminToken)) {
            Instant now = Instant.now();
            return Optional.of(new DashboardSessions.Session("token", "", null, "admin-token",
                    Set.copyOf(DashboardPermission.ALL), now, now.plus(DashboardSessions.LIFETIME), "script"));
        }
        return Optional.empty();
    }

    public void authenticate(Context ctx) {
        String path = ctx.path();
        if (path.equals("/api/health") || path.equals("/api/auth/login") || path.equals("/api/auth/token")) {
            return;
        }
        Optional<DashboardSessions.Session> s = resolve(ctx.cookie(COOKIE), ctx.header("Authorization"));
        if (s.isEmpty() || !s.get().can(DashboardPermission.VIEW)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "unauthorized"));
            ctx.skipRemainingHandlers();
            return;
        }
        ctx.attribute(ATTR, s.get());
    }

    public static DashboardSessions.Session session(Context ctx) {
        return ctx.attribute(ATTR);
    }

    public Handler guard(String permission, Handler handler) {
        return ctx -> {
            DashboardSessions.Session s = session(ctx);
            if (s == null || !s.can(permission)) {
                ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "missing permission " + permission));
                return;
            }
            handler.handle(ctx);
        };
    }

    public void audit(Context ctx, String action) {
        DashboardSessions.Session s = session(ctx);
        // Ids in the action come from the request; keep them from forging log lines.
        String clean = action.replaceAll("[\\p{Cntrl}]", "?");
        logger.info("[dashboard] " + (s == null ? "?" : s.name()) + ": " + clean);
        if (staffSink != null) {
            staffSink.accept((s == null ? "?" : s.name()) + " (dashboard)", clean);
        }
    }

    private volatile java.util.function.BiConsumer<String, String> staffSink;

    public void setStaffSink(java.util.function.BiConsumer<String, String> sink) {
        this.staffSink = sink;
    }

    public static final class LoginBody {
        public String code;
    }

    public static final class TokenBody {
        public String token;
    }

    public void register(io.javalin.Javalin app) {
        app.post("/api/auth/login", ctx -> {
            if (limited(ctx)) {
                return;
            }
            LoginBody body = ctx.bodyAsClass(LoginBody.class);
            Optional<LoginCodes.Grant> grant = body == null || body.code == null
                    ? Optional.empty() : codes.redeem(body.code);
            if (grant.isEmpty() || !grant.get().permissions().contains(DashboardPermission.VIEW)) {
                failed(ctx);
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "invalid or expired code"));
                return;
            }
            LoginCodes.Grant g = grant.get();
            startSession(ctx, sessions.create(g.uuid(), g.name(), g.permissions(), ctx.ip()));
        });
        app.post("/api/auth/token", ctx -> {
            if (limited(ctx)) {
                return;
            }
            TokenBody body = ctx.bodyAsClass(TokenBody.class);
            if (adminToken.isEmpty() || body == null || body.token == null
                    || !constantTimeEquals(body.token.trim(), adminToken)) {
                failed(ctx);
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "invalid token"));
                return;
            }
            startSession(ctx, sessions.create(null, "admin-token", Set.copyOf(DashboardPermission.ALL), ctx.ip()));
        });
        app.get("/api/auth/me", ctx -> ctx.json(me(session(ctx))));
        app.post("/api/auth/logout", ctx -> {
            sessions.remove(ctx.cookie(COOKIE));
            ctx.removeCookie(COOKIE, "/");
            ctx.json(Map.of("ok", true));
        });
        app.get("/api/auth/sessions", guard(DashboardPermission.SESSIONS, ctx -> {
            List<Map<String, Object>> out = new java.util.ArrayList<>();
            DashboardSessions.Session self = session(ctx);
            for (DashboardSessions.Session s : sessions.list()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", s.id());
                m.put("name", s.name());
                m.put("uuid", s.uuid() == null ? null : s.uuid().toString());
                m.put("permissions", s.permissions());
                m.put("created", s.created().toString());
                m.put("expires", s.expires().toString());
                m.put("address", s.address());
                m.put("current", self != null && self.id().equals(s.id()));
                out.add(m);
            }
            ctx.json(out);
        }));
        app.delete("/api/auth/sessions/{id}", guard(DashboardPermission.SESSIONS, ctx -> {
            boolean removed = sessions.revoke(ctx.pathParam("id"));
            audit(ctx, "signed out session " + ctx.pathParam("id"));
            ctx.status(removed ? HttpStatus.OK : HttpStatus.NOT_FOUND).json(Map.of("ok", removed));
        }));
    }

    private void startSession(Context ctx, DashboardSessions.Session s) {
        boolean https = "https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto")) || ctx.scheme().equals("https");
        ctx.cookie(new Cookie(COOKIE, s.token(), "/", (int) DashboardSessions.LIFETIME.toSeconds(), https, 0, true,
                null, null, SameSite.STRICT));
        logger.info("[dashboard] " + s.name() + " signed in from " + s.address());
        ctx.json(me(s));
    }

    private static Map<String, Object> me(DashboardSessions.Session s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("uuid", s.uuid() == null ? null : s.uuid().toString());
        m.put("permissions", new LinkedHashSet<>(s.permissions()));
        m.put("expires", s.expires().toString());
        return m;
    }

    private int[] failures(Context ctx) {
        long minute = System.currentTimeMillis() / 60_000;
        if (minute != attemptsMinute) {
            attempts.clear();
            attemptsMinute = minute;
        }
        return attempts.computeIfAbsent(ctx.ip(), k -> new int[1]);
    }

    private void failed(Context ctx) {
        failures(ctx)[0]++;
    }

    private boolean limited(Context ctx) {
        if (failures(ctx)[0] >= MAX_FAILURES_PER_MINUTE) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS).json(Map.of("error", "too many attempts, wait a minute"));
            return true;
        }
        return false;
    }

    static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
