package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardSessions;
import me.playbosswar.tea.core.impl.settings.SettingsSchema;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.Change;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.FileView;

/** Secret values (webhook urls) are never sent to the browser, only whether they're set. */
final class DashboardSettingsApi {

    static final String HIDDEN = "********";

    private final SettingsServiceImpl settings;
    private final DashboardAuth auth;

    DashboardSettingsApi(SettingsServiceImpl settings, DashboardAuth auth) {
        this.settings = settings;
        this.auth = auth;
    }

    void register(Javalin app) {
        app.get("/api/settings", auth.guard(DashboardPermission.SETTINGS, this::list));
        app.post("/api/settings/{fileId}", auth.guard(DashboardPermission.SETTINGS, this::save));
    }

    private void list(Context ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FileView v : settings.views().values()) {
            Map<String, Object> values = new LinkedHashMap<>(v.values());
            for (SettingsSchema.Field f : v.schema().fields()) {
                if (f.secret()) {
                    Object value = values.get(f.path());
                    values.put(f.path(), value == null || String.valueOf(value).isEmpty() ? "" : HIDDEN);
                }
            }
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("id", v.schema().id());
            file.put("title", v.schema().title());
            file.put("hosts", v.hosts());
            file.put("divergent", v.divergent());
            file.put("schema", v.schema().source());
            file.put("values", values);
            out.add(file);
        }
        ctx.json(out);
    }

    @SuppressWarnings("unchecked")
    private void save(Context ctx) {
        String fileId = ctx.pathParam("fileId");
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (RuntimeException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        List<Change> changes = new ArrayList<>();
        if (body.get("changes") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("path") != null && !HIDDEN.equals(m.get("value"))) {
                    changes.add(new Change(String.valueOf(m.get("path")), m.get("value")));
                }
            }
        }
        if (changes.isEmpty()) {
            ctx.json(Map.of("ok", true, "targets", List.of()));
            return;
        }
        DashboardSessions.Session session = DashboardAuth.session(ctx);
        String actor = (session == null ? "?" : session.name()) + " (dashboard)";
        Object server = body.get("server");
        try {
            List<String> targets = settings.apply(fileId, changes, actor,
                    server instanceof String s && !s.isBlank() ? s : null);
            List<String> paths = changes.stream().map(Change::path).toList();
            auth.audit(ctx, "changed settings " + fileId + " " + paths + " on " + targets);
            ctx.json(Map.of("ok", true, "targets", targets));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
