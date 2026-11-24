package me.playbosswar.tea.core.impl.dashboard;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.game.MapSelectionPolicy;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.profile.PlayerProfile;
import me.playbosswar.tea.core.api.stats.StatsProvider;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardAuth;
import me.playbosswar.tea.core.impl.dashboard.auth.DashboardPermission;
import me.playbosswar.tea.core.impl.dashboard.dto.ArenaDto;
import me.playbosswar.tea.core.impl.dashboard.dto.GameSummaryDto;
import me.playbosswar.tea.core.impl.dashboard.dto.MapDto;
import me.playbosswar.tea.core.impl.dashboard.dto.PlayerProfileDto;
import me.playbosswar.tea.core.impl.dashboard.dto.PlayerRefDto;
import me.playbosswar.tea.core.impl.dashboard.dto.QueueEntryDto;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Reads come from the {@link CoordinationBackend} so the dashboard sees the whole
 * fleet. Control actions publish {@link ControlCommand}s to the owning server; in
 * standalone mode the in-memory backend dispatches them synchronously.
 */
public final class DashboardApi {

    private final TeaCoreAPI core;
    private final TeaCorePlugin plugin;
    private final DashboardAuth auth;
    private final DashboardEventBroadcaster events;

    public DashboardApi(TeaCoreAPI core, TeaCorePlugin plugin, DashboardAuth auth, DashboardEventBroadcaster events) {
        this.core = core;
        this.plugin = plugin;
        this.auth = auth;
        this.events = events;
    }

    public void register(Javalin app) {
        app.get("/api/games", this::listGames);
        app.get("/api/leaderboard/{gameId}", this::leaderboard);
        app.get("/api/arenas", this::listArenas);
        // Arena detail, feeds and player actions: see DashboardLiveApi.
        app.get("/api/queues", this::listQueues);
        app.get("/api/queues/{gameId}", this::getQueue);
        app.get("/api/maps", this::listAllMaps);
        app.get("/api/maps/{gameId}", this::listMapsForGame);
        app.get("/api/players/online", this::onlinePlayers);
        app.get("/api/players/{uuid}", this::getPlayer);
        app.get("/api/stats/{gameId}/{uuid}", this::getStatsForPlayer);

        // Control actions answer 202 Accepted; the dashboard watches the event stream for
        // confirmation.
        app.post("/api/arenas", auth.guard(DashboardPermission.ARENA_ALLOCATE, this::allocateArena));
        app.post("/api/arenas/{arenaId}/start", auth.guard(DashboardPermission.ARENA_START, this::forceStartArena));
        app.post("/api/arenas/{arenaId}/release", auth.guard(DashboardPermission.ARENA_RELEASE, this::releaseArena));
        app.post("/api/arenas/{arenaId}/end-match", auth.guard(DashboardPermission.ARENA_END, this::endArenaMatch));
        app.delete("/api/queues/{gameId}/{playerUuid}", auth.guard(DashboardPermission.QUEUE_REMOVE, this::dequeuePlayer));
        app.post("/api/maps/reload", auth.guard(DashboardPermission.MAPS_RELOAD, this::reloadMaps));
        app.delete("/api/maps/{gameId}/{mapId}", auth.guard(DashboardPermission.MAPS_DELETE, this::deleteMap));
        app.get("/api/maps/{gameId}/{mapId}/versions", this::mapVersions);
        app.post("/api/maps/{gameId}/{mapId}/restore", auth.guard(DashboardPermission.MAPS_RESTORE, this::restoreMap));
        app.get("/api/maps/{gameId}/{mapId}/download", auth.guard(DashboardPermission.MAPS_TRANSFER, this::downloadMap));
        app.get("/api/maps-export", auth.guard(DashboardPermission.MAPS_TRANSFER, this::exportMaps));
        app.post("/api/maps-upload", auth.guard(DashboardPermission.MAPS_TRANSFER, this::uploadMaps));
        new DashboardLiveApi(plugin, auth, events, this).register(app);
        new DashboardHistoryApi(plugin, auth).register(app);
        DashboardServersApi servers = new DashboardServersApi(core.coordination(), auth);
        servers.register(app);
        new DashboardChartsApi(plugin, core.database().dataSource(), core.coordination(), auth,
                servers::onlinePlayers, plugin.getLogger()).register(app);
        if (plugin.settingsService() != null) {
            new DashboardSettingsApi(plugin.settingsService(), auth).register(app);
        }
    }

    private void listGames(Context ctx) {
        List<GameSummaryDto> games = new ArrayList<>();
        for (GameId id : fleetGames()) {
            games.add(core.games().get(id).map(this::toDto).orElseGet(() -> remoteDto(id)));
        }
        ctx.json(games);
    }

    /**
     * Includes games other servers host: the dashboard usually runs on the lobby,
     * which loads no game plugins.
     */
    private java.util.Set<GameId> fleetGames() {
        java.util.Set<GameId> ids = new java.util.LinkedHashSet<>();
        for (GameDefinition def : core.games().all()) {
            ids.add(def.id());
        }
        ids.addAll(core.coordination().allHostedGames());
        return ids;
    }

    private GameSummaryDto remoteDto(GameId id) {
        org.bukkit.configuration.ConfigurationSection cfg = org.bukkit.Bukkit.getPluginManager()
                .getPlugin("TeaCore").getConfig().getConfigurationSection("arena-pool.games." + id.value());
        return new GameSummaryDto(
                id.value(),
                id.value(),
                "REMOTE",
                cfg == null ? 0 : cfg.getInt("min-available", 0),
                cfg == null ? 0 : cfg.getInt("max-total", 0),
                0, 0, 0, 0, 0, 0,
                "REMOTE",
                "random",
                core.maps().forGame(id).size(),
                java.util.List.copyOf(new java.util.TreeSet<>(core.coordination().hostsOf(id))));
    }

    private GameSummaryDto toDto(GameDefinition def) {
        String selection = def.mapSelection() instanceof MapSelectionPolicy.Fixed fixed
                ? "fixed:" + fixed.mapId() : "random";
        int mapCount = core.maps().forGame(def.id()).size();
        String key = def.displayNameKey();
        String name = core.messageService().hasKey(key)
                ? core.messageService().renderPlainText(plugin.getConfig().getString("i18n.default-locale", "en_US"), key,
                        Map.of())
                : def.id().value();
        return new GameSummaryDto(
                def.id().value(),
                name,
                def.teamMode().name(),
                def.pool().minAvailable(),
                def.pool().maxTotal(),
                def.pool().preloadOnStartup(),
                def.lifecycle().maxWaitSeconds(),
                def.lifecycle().countdownSeconds(),
                def.lifecycle().startingSeconds(),
                def.lifecycle().endingSeconds(),
                def.lifecycle().maxMatchSeconds(),
                def.endMode().name(),
                selection,
                mapCount,
                java.util.List.of());
    }

    private void listArenas(Context ctx) {
        List<ArenaDto> out = new ArrayList<>();
        for (ArenaSnapshot snapshot : core.coordination().allArenas()) {
            out.add(toDto(snapshot));
        }
        out.sort(Comparator.comparing(ArenaDto::gameId).thenComparing(ArenaDto::id));
        ctx.json(out);
    }

    ArenaDto toDto(ArenaSnapshot snapshot) {
        String mapId = snapshot.mapId() == null || snapshot.mapId().isEmpty() ? null : snapshot.mapId();
        // Only snapshots from this server can be enriched from the local manager.
        if (snapshot.serverId().equals(core.coordination().serverId())) {
            Arena local = core.arenas().get(snapshot.arenaId()).orElse(null);
            if (local != null) {
                return new ArenaDto(
                        local.id().value().toString(),
                        local.gameId().value(),
                        snapshot.serverId(),
                        local.mapId().orElse(null),
                        local.state().name(),
                        local.matchId().toString(),
                        local.phaseSecondsRemaining(),
                        local.minPlayers(),
                        local.maxPlayers(),
                        toPlayerRefs(local.players()),
                        toPlayerRefs(local.spectators()),
                        local.world().map(w -> w.getName()).orElse(null));
            }
        }
        List<PlayerRefDto> players = new ArrayList<>();
        List<PlayerRefDto> spectators = new ArrayList<>();
        for (ArenaSnapshot.RosterEntry r : snapshot.roster()) {
            (r.spectator() ? spectators : players).add(new PlayerRefDto(r.uuid().toString(), r.name()));
        }
        return new ArenaDto(
                snapshot.arenaId().value().toString(),
                snapshot.gameId().value(),
                snapshot.serverId(),
                mapId,
                snapshot.state().name(),
                null,
                snapshot.secondsRemaining(),
                snapshot.minPlayers(),
                snapshot.maxPlayers(),
                players,
                spectators,
                null);
    }

    private List<PlayerRefDto> toPlayerRefs(java.util.Set<UUID> uuids) {
        List<PlayerRefDto> out = new ArrayList<>(uuids.size());
        for (UUID u : uuids) {
            Player p = Bukkit.getPlayer(u);
            String name = p != null ? p.getName() : nameOf(u);
            out.add(new PlayerRefDto(u.toString(), name));
        }
        return out;
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName();
    }

    private void listQueues(Context ctx) {
        Map<String, List<QueueEntryDto>> out = new LinkedHashMap<>();
        CoordinationBackend backend = core.coordination();
        for (GameId id : fleetGames()) {
            List<QueueEntryDto> entries = queueEntriesFor(backend, id);
            if (!entries.isEmpty()) out.put(id.value(), entries);
        }
        ctx.json(out);
    }

    private void getQueue(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        ctx.json(queueEntriesFor(core.coordination(), gameId));
    }

    private List<QueueEntryDto> queueEntriesFor(CoordinationBackend backend, GameId gameId) {
        List<QueueEntry> raw = new ArrayList<>(backend.queueFor(gameId));
        List<QueueEntryDto> out = new ArrayList<>(raw.size());
        int i = 0;
        for (QueueEntry e : raw) {
            out.add(new QueueEntryDto(
                    e.playerUuid().toString(),
                    e.playerName(),
                    e.gameId().value(),
                    e.mapIds(),
                    e.queuedAt().toString(),
                    i));
            i++;
        }
        return out;
    }

    private void listAllMaps(Context ctx) {
        List<MapDto> out = new ArrayList<>();
        for (MapDefinition def : core.maps().all()) {
            out.add(toDto(def));
        }
        out.sort(Comparator.comparing(MapDto::gameId).thenComparing(MapDto::mapId));
        ctx.json(out);
    }

    private void listMapsForGame(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        List<MapDto> out = new ArrayList<>();
        for (MapDefinition def : core.maps().forGame(gameId)) {
            out.add(toDto(def));
        }
        ctx.json(out);
    }

    private MapDto toDto(MapDefinition def) {
        List<String> extensions = new ArrayList<>(def.extensions().keySet());
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : def.extensions().entrySet()) {
            descriptions.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new MapDto(
                def.gameId().value(),
                def.mapId(),
                def.schematic().map(java.nio.file.Path::getFileName).map(Object::toString).orElse(null),
                def.minPlayers(),
                def.maxPlayers(),
                def.participantSpawns().size(),
                def.waitingSpawn() != null,
                extensions,
                descriptions);
    }

    private void getPlayer(Context ctx) {
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid uuid"));
            return;
        }
        Optional<PlayerProfile> profile = core.playerProfiles().get(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (profile.isEmpty() && online == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "player not found"));
            return;
        }

        // Iterate persisted schemas so the lobby's dashboard shows every game in the fleet,
        // not only games loaded here.
        Map<String, Map<String, String>> statsByGame = new LinkedHashMap<>();
        for (var schema : core.stats().allSchemas()) {
            Map<String, String> entries = core.stats().providerFor(schema).fetch(uuid);
            if (!entries.isEmpty()) {
                statsByGame.put(schema.gameId().value(), entries);
            }
        }

        String name = online != null ? online.getName() : nameOf(uuid);
        ctx.json(new PlayerProfileDto(
                uuid.toString(),
                name,
                online != null,
                profile.map(p -> p.firstSeen().toString()).orElse(null),
                profile.map(p -> p.lastSeen().toString()).orElse(null),
                profile.map(PlayerProfile::locale).orElse(null),
                statsByGame));
    }

    private void onlinePlayers(Context ctx) {
        List<PlayerRefDto> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(new PlayerRefDto(p.getUniqueId().toString(), p.getName()));
        }
        ctx.json(out);
    }

    private void getStatsForPlayer(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid uuid"));
            return;
        }
        // Fall back to a schema-driven generic provider so this works on a lobby with no
        // game plugin loaded.
        StatsProvider provider = core.stats().forGame(gameId).orElse(null);
        if (provider == null) {
            var schema = core.stats().allSchemas().stream()
                    .filter(s -> s.gameId().equals(gameId))
                    .findFirst()
                    .orElse(null);
            if (schema == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "no stats schema for " + gameId.value()));
                return;
            }
            provider = core.stats().providerFor(schema);
        }
        ctx.json(provider.fetch(uuid));
    }

    private void allocateArena(Context ctx) {
        AllocateBody body;
        try {
            body = ctx.bodyAsClass(AllocateBody.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid body"));
            return;
        }
        if (body == null || body.gameId == null || body.gameId.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "gameId is required"));
            return;
        }
        String target = body.serverId != null && !body.serverId.isBlank()
                ? body.serverId
                : pickAllocationTarget(body.gameId);
        if (target == null) {
            ctx.status(HttpStatus.CONFLICT).json(Map.of(
                    "error", "no backend server is currently hosting game " + body.gameId
                            + "; specify serverId or ensure at least one arena backend is online."));
            return;
        }
        core.coordination().publishControl(target, ControlCommand.of(
                ControlCommand.Type.ALLOCATE_ARENA,
                null, body.gameId, body.mapId, null));
        auth.audit(ctx, "allocate " + body.gameId + (body.mapId == null ? "" : "/" + body.mapId) + " on " + target);
        ctx.status(HttpStatus.ACCEPTED).json(Map.of(
                "ok", true, "serverId", target,
                "note", "Arena allocation dispatched; watch the event stream for the new arena."));
    }

    private void forceStartArena(Context ctx) {
        routeArenaCommand(ctx, ControlCommand.Type.START_ARENA);
    }

    private void releaseArena(Context ctx) {
        routeArenaCommand(ctx, ControlCommand.Type.RELEASE_ARENA);
    }

    private void endArenaMatch(Context ctx) {
        ArenaId arenaId = parseArenaId(ctx);
        if (arenaId == null) return;
        ArenaSnapshot snapshot = findSnapshot(arenaId);
        if (snapshot == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "arena not found"));
            return;
        }
        EndMatchBody body;
        try {
            body = ctx.bodyAsClass(EndMatchBody.class);
        } catch (Exception e) {
            body = new EndMatchBody();
        }
        if (body == null) body = new EndMatchBody();
        if (snapshot.state() != me.playbosswar.tea.core.api.arena.ArenaState.LIVE) {
            ctx.status(HttpStatus.CONFLICT).json(Map.of("error", "no match running in this arena"));
            return;
        }
        String team = body.winnerTeam;
        String winner = body.winnerUuid;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.arenaAdmin().end(snapshot, team, winner));
        auth.audit(ctx, "end match in arena " + arenaId.value() + " (winner "
                + (team != null ? "team " + team : winner != null ? winner : "draw") + ")");
        ctx.status(HttpStatus.ACCEPTED).json(Map.of("ok", true, "serverId", snapshot.serverId()));
    }

    private void routeArenaCommand(Context ctx, ControlCommand.Type type) {
        ArenaId arenaId = parseArenaId(ctx);
        if (arenaId == null) return;
        ArenaSnapshot snapshot = findSnapshot(arenaId);
        if (snapshot == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "arena not found"));
            return;
        }
        core.coordination().publishControl(snapshot.serverId(), ControlCommand.of(
                type, arenaId.value().toString(), null, null, null));
        auth.audit(ctx, type.name().toLowerCase(java.util.Locale.ROOT) + " arena " + arenaId.value());
        ctx.status(HttpStatus.ACCEPTED).json(Map.of("ok", true, "serverId", snapshot.serverId()));
    }

    private void dequeuePlayer(Context ctx) {
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.pathParam("playerUuid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid uuid"));
            return;
        }
        // The queue lives in the coordination backend, so mutating it from the Jetty worker
        // thread is safe.
        core.coordination().dequeue(uuid);
        auth.audit(ctx, "removed " + uuid + " from the queue");
        ctx.json(Map.of("ok", true));
    }

    private void reloadMaps(Context ctx) {
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        targets.add(core.coordination().serverId());
        for (ArenaSnapshot s : core.coordination().allArenas()) targets.add(s.serverId());
        for (String target : targets) {
            core.coordination().publishControl(target, ControlCommand.of(
                    ControlCommand.Type.RELOAD_MAPS, null, null, null, null));
        }
        auth.audit(ctx, "reload maps on " + targets);
        ctx.json(Map.of("ok", true, "targets", targets));
    }

    private void downloadMap(Context ctx) {
        String game = ctx.pathParam("gameId");
        String map = ctx.pathParam("mapId");
        try {
            sendZip(ctx, plugin.mapTransfer().exportMap(new GameId(game), map), game + "-" + map + ".zip");
        } catch (java.io.IOException e) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void exportMaps(Context ctx) {
        try {
            sendZip(ctx, plugin.mapTransfer().exportAll(), "tea-maps.zip");
        } catch (java.io.IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private static void sendZip(Context ctx, byte[] zip, String name) {
        ctx.contentType("application/zip");
        ctx.header("Content-Disposition", "attachment; filename=\"" + name.replaceAll("[^A-Za-z0-9_.-]", "_") + "\"");
        ctx.result(zip);
    }

    private void uploadMaps(Context ctx) {
        byte[] zip = ctx.bodyAsBytes();
        if (zip.length == 0) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "empty upload"));
            return;
        }
        var session = DashboardAuth.session(ctx);
        String actor = (session == null ? "?" : session.name()) + " (dashboard)";
        try {
            var result = MainThread.runSync(plugin, () -> {
                try {
                    return plugin.importMaps(zip, actor);
                } catch (java.io.IOException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }, 60_000L);
            auth.audit(ctx, "uploaded maps " + result.maps() + " and waiting rooms " + result.rooms());
            ctx.json(Map.of("ok", true, "maps", result.maps(), "rooms", result.rooms()));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void deleteMap(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        String mapId = ctx.pathParam("mapId");
        if (core.maps().get(gameId, mapId).isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "map not found"));
            return;
        }
        // Map definitions are per-plugin in-memory state, so every hosting server must
        // drop the entry.
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        targets.add(core.coordination().serverId());
        // With shared map storage one server changes the files and the others pull them.
        for (ArenaSnapshot s : plugin.sharedFiles() != null ? List.<ArenaSnapshot>of() : core.coordination().allArenas()) {
            if (s.gameId().equals(gameId)) targets.add(s.serverId());
        }
        for (String target : targets) {
            core.coordination().publishControl(target, ControlCommand.of(
                    ControlCommand.Type.DELETE_MAP, null, gameId.value(), mapId, null));
        }
        auth.audit(ctx, "removed map " + gameId.value() + "/" + mapId);
        ctx.json(Map.of("ok", true, "targets", targets));
    }

    private void mapVersions(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        String mapId = ctx.pathParam("mapId");
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            for (var v : plugin.mapEditingService().backups().list(gameId, mapId)) {
                out.add(Map.of("number", v.number(), "when", v.when().toString(),
                        "actor", v.actor(), "reason", v.reason()));
            }
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid map id"));
            return;
        }
        ctx.json(out);
    }

    private void restoreMap(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        String mapId = ctx.pathParam("mapId");
        int number;
        try {
            number = ctx.bodyAsClass(RestoreRequest.class).version();
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "body must be {\"version\": n}"));
            return;
        }
        if (plugin.mapEditingService().backups().get(gameId, mapId, number).isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "no such version"));
            return;
        }
        // Each hosting server keeps its own copy of the map, so restore on all
        // of them (they hold the same versions when the maps folder is shared).
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        targets.add(core.coordination().serverId());
        // With shared map storage one server changes the files and the others pull them.
        for (ArenaSnapshot s : plugin.sharedFiles() != null ? List.<ArenaSnapshot>of() : core.coordination().allArenas()) {
            if (s.gameId().equals(gameId)) targets.add(s.serverId());
        }
        for (String target : targets) {
            core.coordination().publishControl(target, new ControlCommand(ControlCommand.Type.RESTORE_MAP,
                    null, gameId.value(), mapId, null, null, null, String.valueOf(number)));
        }
        auth.audit(ctx, "restored map " + gameId.value() + "/" + mapId + " to version #" + number);
        ctx.json(Map.of("ok", true, "targets", targets));
    }

    record RestoreRequest(int version) { }

    private void leaderboard(Context ctx) {
        GameId gameId;
        try {
            gameId = GameId.of(ctx.pathParam("gameId"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid game id"));
            return;
        }
        int limit = 50;
        try {
            String raw = ctx.queryParam("limit");
            if (raw != null) {
                limit = Math.max(1, Math.min(200, Integer.parseInt(raw)));
            }
        } catch (NumberFormatException ignored) {
            // keep 50
        }
        if (plugin.ratings() == null) {
            ctx.json(List.of());
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var row : plugin.ratings().leaderboard(gameId, limit)) {
            out.add(Map.of("rank", row.rank(), "uuid", row.uuid().toString(), "name", row.name(),
                    "rating", row.rating(), "games", row.games(), "wins", row.wins(), "losses", row.losses()));
        }
        ctx.json(out);
    }

    @Nullable
    ArenaId parseArenaId(Context ctx) {
        try {
            return new ArenaId(UUID.fromString(ctx.pathParam("arenaId")));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid arena id"));
            return null;
        }
    }

    @Nullable
    ArenaSnapshot findSnapshot(ArenaId arenaId) {
        for (ArenaSnapshot s : core.coordination().allArenas()) {
            if (s.arenaId().equals(arenaId)) return s;
        }
        return null;
    }

    /**
     * Prefers this server if it registered the game, then any server publishing an
     * arena for it.
     */
    @Nullable
    private String pickAllocationTarget(String gameIdStr) {
        GameId gameId = new GameId(gameIdStr);
        String local = core.coordination().serverId();
        if (core.games().get(gameId).isPresent()) {
            return local;
        }
        for (ArenaSnapshot s : core.coordination().allArenas()) {
            if (s.gameId().equals(gameId)) return s.serverId();
        }
        return null;
    }

    public static final class AllocateBody {
        public String gameId;
        @Nullable public String mapId;
        /** If omitted, the server picks a target from game registration. */
        @Nullable public String serverId;
    }

    public static final class EndMatchBody {
        @Nullable public String winnerUuid;
        /** Takes precedence over {@code winnerUuid}. */
        @Nullable public String winnerTeam;
    }
}
