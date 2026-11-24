package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.history.MatchHistoryService;
import me.playbosswar.tea.core.api.history.MatchHistoryService.MatchRecord;
import me.playbosswar.tea.core.api.history.MatchHistoryService.PlayerMatch;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;

/**
 * {@code from}/{@code to} are UTC dates ({@code 2026-11-19}) and {@code to} includes
 * that day.
 */
final class DashboardHistoryApi {

    private static final int CSV_MAX_MATCHES = 10_000;

    private final TeaCorePlugin plugin;
    private final DashboardAuth auth;

    DashboardHistoryApi(TeaCorePlugin plugin, DashboardAuth auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    private MatchHistoryService history() {
        return plugin.matchHistory();
    }

    void register(Javalin app) {
        app.get("/api/history", this::list);
        app.get("/api/history.csv", auth.guard(DashboardPermission.HISTORY_EXPORT, this::csv));
        app.get("/api/history/{matchId}", this::detail);
        app.get("/api/players/{uuid}/history", this::playerHistory);
    }

    private MatchHistoryService.Query query(Context ctx, int defaultLimit) {
        return new MatchHistoryService.Query(
                blankToNull(ctx.queryParam("game")),
                blankToNull(ctx.queryParam("map")),
                blankToNull(ctx.queryParam("player")),
                date(ctx.queryParam("from"), 0),
                date(ctx.queryParam("to"), 1),
                intParam(ctx, "limit", defaultLimit),
                intParam(ctx, "offset", 0));
    }

    private void list(Context ctx) {
        MatchHistoryService.Query raw = query(ctx, 50);
        // Each row costs a player query; keep pages small.
        MatchHistoryService.Query q = new MatchHistoryService.Query(raw.game(), raw.map(), raw.player(), raw.from(),
                raw.to(), Math.min(100, raw.limit()), raw.offset());
        List<Map<String, Object>> matches = new ArrayList<>();
        for (MatchRecord m : history().search(q)) {
            Map<String, Object> row = match(m);
            List<PlayerMatch> players = history().participants(m.matchId());
            row.put("players", players.size());
            row.put("winner", winnerLabel(m, players));
            matches.add(row);
        }
        ctx.json(Map.of("total", history().count(q), "matches", matches));
    }

    private void detail(Context ctx) {
        UUID id;
        try {
            id = UUID.fromString(ctx.pathParam("matchId"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid match id"));
            return;
        }
        List<PlayerMatch> players = history().participants(id);
        if (players.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "match not found"));
            return;
        }
        MatchRecord m = players.get(0).match();
        Map<String, Object> out = match(m);
        out.put("winner", winnerLabel(m, players));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PlayerMatch p : players) {
            lines.add(line(p));
        }
        out.put("players", lines);
        ctx.json(out);
    }

    private void playerHistory(Context ctx) {
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid uuid"));
            return;
        }
        List<PlayerMatch> recent = history().recentFor(uuid, Math.min(100, intParam(ctx, "limit", 25)));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlayerMatch p : recent) {
            Map<String, Object> row = match(p.match());
            row.putAll(line(p));
            rows.add(row);
        }
        String streakResult = recent.isEmpty() ? null : recent.get(0).result();
        int streak = 0;
        for (PlayerMatch p : recent) {
            if (!p.result().equals(streakResult)) {
                break;
            }
            streak++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matches", rows);
        out.put("streak", Map.of("result", streakResult == null ? "none" : streakResult, "count", streak));
        ctx.json(out);
    }

    private void csv(Context ctx) {
        MatchHistoryService.Query base = query(ctx, CSV_MAX_MATCHES);
        MatchHistoryService.Query q = new MatchHistoryService.Query(base.game(), base.map(), base.player(),
                base.from(), base.to(), CSV_MAX_MATCHES, 0);
        StringBuilder sb = new StringBuilder(
                "match_id,ended_at,game,map,server,duration_seconds,winner_team,player_uuid,player,team,kills,deaths,assists,result\n");
        int rows = 0;
        for (MatchRecord m : allPages(q)) {
            for (PlayerMatch p : history().participants(m.matchId())) {
                sb.append(m.matchId()).append(',').append(m.endedAt()).append(',')
                        .append(cell(m.gameId())).append(',').append(cell(m.mapId())).append(',')
                        .append(cell(m.serverId())).append(',').append(m.durationSeconds()).append(',')
                        .append(cell(m.winnerTeam())).append(',')
                        .append(p.uuid() == null ? "" : p.uuid()).append(',').append(cell(p.name())).append(',')
                        .append(cell(p.team())).append(',').append(p.kills()).append(',').append(p.deaths())
                        .append(',').append(p.assists()).append(',').append(p.result()).append('\n');
                rows++;
            }
        }
        auth.audit(ctx, "exported " + rows + " match history rows");
        ctx.header("Content-Disposition", "attachment; filename=\"match-history.csv\"");
        ctx.contentType("text/csv; charset=utf-8");
        ctx.result(sb.toString());
    }

    private List<MatchRecord> allPages(MatchHistoryService.Query q) {
        List<MatchRecord> out = new ArrayList<>();
        while (out.size() < q.limit()) {
            List<MatchRecord> page = history().search(new MatchHistoryService.Query(q.game(), q.map(), q.player(),
                    q.from(), q.to(), 500, out.size()));
            out.addAll(page);
            if (page.size() < 500) {
                break;
            }
        }
        return out;
    }

    private static Map<String, Object> match(MatchRecord m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("matchId", m.matchId().toString());
        row.put("game", m.gameId());
        row.put("map", m.mapId());
        row.put("server", m.serverId());
        row.put("endedAt", m.endedAt().toString());
        row.put("durationSeconds", m.durationSeconds());
        row.put("winnerTeam", m.winnerTeam());
        row.put("winnerUuid", m.winnerUuid() == null ? null : m.winnerUuid().toString());
        return row;
    }

    private static Map<String, Object> line(PlayerMatch p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uuid", p.uuid() == null ? null : p.uuid().toString());
        row.put("name", p.name());
        row.put("team", p.team());
        row.put("kills", p.kills());
        row.put("deaths", p.deaths());
        row.put("assists", p.assists());
        row.put("result", p.result());
        return row;
    }

    private static String winnerLabel(MatchRecord m, List<PlayerMatch> players) {
        if (m.winnerTeam() != null) {
            return m.winnerTeam();
        }
        if (m.winnerUuid() != null) {
            for (PlayerMatch p : players) {
                if (m.winnerUuid().equals(p.uuid()) && p.name() != null) {
                    return p.name();
                }
            }
            return m.winnerUuid().toString().substring(0, 8);
        }
        return "draw";
    }

    /** Quoted when it holds a comma, quote or newline; formula-looking text is neutralized. */
    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Instant date(String raw, int plusDays) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).plusDays(plusDays).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int intParam(Context ctx, String name, int fallback) {
        try {
            String raw = ctx.queryParam(name);
            return raw == null ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
