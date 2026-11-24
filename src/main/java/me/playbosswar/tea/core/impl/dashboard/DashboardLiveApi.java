package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.admin.ArenaAdmin;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardSessions;
import org.bukkit.Bukkit;

final class DashboardLiveApi {

    private final TeaCorePlugin plugin;
    private final DashboardAuth auth;
    private final DashboardEventBroadcaster events;
    private final DashboardApi api;

    DashboardLiveApi(TeaCorePlugin plugin, DashboardAuth auth, DashboardEventBroadcaster events, DashboardApi api) {
        this.plugin = plugin;
        this.auth = auth;
        this.events = events;
        this.api = api;
    }

    void register(Javalin app) {
        app.get("/api/arenas/{arenaId}", this::detail);
        app.get("/api/feed/kills", ctx -> ctx.json(events.recentKills(null)));
        app.post("/api/arenas/{arenaId}/players/{uuid}/kick", auth.guard(DashboardPermission.PLAYER_KICK,
                ctx -> playerAction(ctx, "kick", (admin, s, player) -> admin.kick(s, player))));
        app.post("/api/arenas/{arenaId}/players/{uuid}/spectator", auth.guard(DashboardPermission.PLAYER_SPECTATOR,
                ctx -> playerAction(ctx, "make spectator", (admin, s, player) -> admin.makeSpectator(s, player))));
        app.post("/api/arenas/{arenaId}/players/{uuid}/move", auth.guard(DashboardPermission.PLAYER_MOVE, this::move));
    }

    private void detail(Context ctx) {
        ArenaId id = api.parseArenaId(ctx);
        if (id == null) {
            return;
        }
        ArenaSnapshot s = api.findSnapshot(id);
        if (s == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "arena not found"));
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("arena", api.toDto(s));
        out.put("mapName", s.mapId() == null || s.mapId().isEmpty() ? null
                : plugin.maps().get(s.gameId(), s.mapId()).map(MapDefinition::resolvedDisplayName).orElse(s.mapId()));
        out.put("secondsRemaining", s.secondsRemaining());
        List<Map<String, Object>> roster = new ArrayList<>();
        Map<String, int[]> teams = new LinkedHashMap<>();
        for (ArenaSnapshot.RosterEntry r : s.roster()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", r.uuid().toString());
            m.put("name", r.name());
            m.put("team", r.team());
            m.put("spectator", r.spectator());
            m.put("kills", r.kills());
            m.put("deaths", r.deaths());
            m.put("assists", r.assists());
            roster.add(m);
            if (!r.spectator() && r.team() != null) {
                int[] t = teams.computeIfAbsent(r.team(), k -> new int[4]);
                t[0]++;
                t[1] += r.kills();
                t[2] += r.deaths();
                t[3] += r.assists();
            }
        }
        out.put("players", roster);
        List<Map<String, Object>> teamList = new ArrayList<>();
        teams.forEach((team, t) -> teamList.add(Map.of("team", team, "players", t[0], "kills", t[1],
                "deaths", t[2], "assists", t[3])));
        teamList.sort((a, b) -> Integer.compare((int) b.get("kills"), (int) a.get("kills")));
        out.put("teams", teamList);
        if (s.sidebar() != null) {
            out.put("sidebar", Map.of("viewer", s.sidebar().viewer(), "title", s.sidebar().title(),
                    "lines", s.sidebar().lines()));
        }
        out.put("kills", events.recentKills(id.value().toString()));
        DashboardSessions.Session session = DashboardAuth.session(ctx);
        if (session != null && session.can(DashboardPermission.CHAT)) {
            out.put("chat", events.recentChat(id.value().toString()));
        }
        ctx.json(out);
    }

    private interface PlayerAction {
        void run(ArenaAdmin admin, ArenaSnapshot arena, UUID player);
    }

    private void playerAction(Context ctx, String label, PlayerAction action) {
        ArenaSnapshot s = arenaOrRespond(ctx);
        UUID player = playerOrRespond(ctx, s);
        if (s == null || player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> action.run(plugin.arenaAdmin(), s, player));
        auth.audit(ctx, label + " " + nameIn(s, player) + " in arena " + s.arenaId().value());
        ctx.status(HttpStatus.ACCEPTED).json(Map.of("ok", true, "serverId", s.serverId()));
    }

    public static final class MoveBody {
        public String targetArenaId;
    }

    private void move(Context ctx) {
        ArenaSnapshot s = arenaOrRespond(ctx);
        UUID player = playerOrRespond(ctx, s);
        if (s == null || player == null) {
            return;
        }
        MoveBody body = ctx.bodyAsClass(MoveBody.class);
        ArenaSnapshot target = body == null || body.targetArenaId == null ? null
                : plugin.arenaAdmin().find(body.targetArenaId).orElse(null);
        if (target == null || target.arenaId().equals(s.arenaId())) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "pick another existing arena"));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.arenaAdmin().move(s, player, target));
        auth.audit(ctx, "move " + nameIn(s, player) + " from arena " + s.arenaId().value()
                + " to " + target.arenaId().value());
        ctx.status(HttpStatus.ACCEPTED).json(Map.of("ok", true));
    }

    private ArenaSnapshot arenaOrRespond(Context ctx) {
        ArenaId id = api.parseArenaId(ctx);
        if (id == null) {
            return null;
        }
        ArenaSnapshot s = api.findSnapshot(id);
        if (s == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "arena not found"));
        }
        return s;
    }

    private static UUID playerOrRespond(Context ctx, ArenaSnapshot s) {
        if (s == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
            if (s.roster().stream().noneMatch(r -> r.uuid().equals(uuid))) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "player is not in this arena"));
                return null;
            }
            return uuid;
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid uuid"));
            return null;
        }
    }

    private static String nameIn(ArenaSnapshot s, UUID player) {
        return s.roster().stream().filter(r -> r.uuid().equals(player)).map(ArenaSnapshot.RosterEntry::name)
                .findFirst().orElse(player.toString());
    }
}
