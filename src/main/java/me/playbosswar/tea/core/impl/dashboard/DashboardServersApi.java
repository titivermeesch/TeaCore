package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;

/** Each server reports itself every 15 seconds; a server silent for a minute shows as offline. */
final class DashboardServersApi {

    private static final long OFFLINE_AFTER_MS = 60_000;
    private static final long FORGET_AFTER_MS = 10 * 60_000;

    private record Report(Map<String, String> data, int players, long at) { }

    private final CoordinationBackend coordination;
    private final DashboardAuth auth;
    private final Map<String, Report> reports = new ConcurrentHashMap<>();

    DashboardServersApi(CoordinationBackend coordination, DashboardAuth auth) {
        this.coordination = coordination;
        this.auth = auth;
        coordination.subscribeEvents(this::onEvent);
    }

    void register(Javalin app) {
        app.get("/api/servers", auth.guard(DashboardPermission.VIEW, this::list));
    }

    private void onEvent(CoordinationEvent e) {
        if (e.type() == CoordinationEvent.Type.SERVER_STATS) {
            reports.put(e.serverId(), new Report(e.data() == null ? Map.of() : Map.copyOf(e.data()),
                    e.playerCount(), System.currentTimeMillis()));
        }
    }

    /** Players on servers that reported in the last minute, or -1 when none did. */
    int onlinePlayers() {
        long now = System.currentTimeMillis();
        int total = 0;
        boolean any = false;
        for (Report r : reports.values()) {
            if (now - r.at < OFFLINE_AFTER_MS) {
                total += r.players;
                any = true;
            }
        }
        return any ? total : -1;
    }

    private void list(Context ctx) {
        long now = System.currentTimeMillis();
        reports.entrySet().removeIf(e -> now - e.getValue().at > FORGET_AFTER_MS);
        Map<String, List<Map<String, Object>>> arenasByServer = new LinkedHashMap<>();
        for (ArenaSnapshot s : coordination.allArenas()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", s.arenaId().value().toString());
            a.put("game", s.gameId().value());
            a.put("map", s.mapId());
            a.put("state", s.state().name());
            a.put("players", s.players());
            a.put("maxPlayers", s.maxPlayers());
            arenasByServer.computeIfAbsent(s.serverId(), k -> new ArrayList<>()).add(a);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Report> e : reports.entrySet()) {
            Report r = e.getValue();
            Map<String, String> d = r.data;
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", e.getKey());
            s.put("online", now - r.at < OFFLINE_AFTER_MS);
            s.put("lastSeen", r.at);
            s.put("players", r.players);
            s.put("tps", number(d.get("tps")));
            s.put("mspt", number(d.get("mspt")));
            s.put("memoryUsed", number(d.get("memoryUsed")));
            s.put("memoryMax", number(d.get("memoryMax")));
            s.put("group", d.getOrDefault("group", ""));
            s.put("lobby", Boolean.parseBoolean(d.get("lobby")));
            s.put("poolMode", d.getOrDefault("poolMode", ""));
            s.put("teacore", d.getOrDefault("teacore", ""));
            s.put("minecraft", d.getOrDefault("minecraft", ""));
            s.put("software", d.getOrDefault("software", ""));
            s.put("installed", split(d.get("installed")));
            s.put("hosted", split(d.get("hosted")));
            Map<String, String> maps = new LinkedHashMap<>();
            for (String pair : split(d.get("maps"))) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    maps.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            s.put("mapModes", maps);
            s.put("arenas", arenasByServer.getOrDefault(e.getKey(), List.of()));
            out.add(s);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> !Boolean.TRUE.equals(m.get("lobby")))
                .thenComparing(m -> String.valueOf(m.get("id"))));
        ctx.json(out);
    }

    private static Double number(String s) {
        try {
            return s == null ? null : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> split(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split(","));
    }
}
