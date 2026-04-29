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
 * Dashboard HTTP surface. Reads are sourced from the {@link CoordinationBackend}
 * so the dashboard sees the entire Tea fleet, not just arenas on the server
 * serving the request. Control actions publish {@link ControlCommand}s to the
 * target backend over the same channel; in standalone mode the in-memory
 * backend dispatches them synchronously.
 */
public final class DashboardApi {

    private final TeaCoreAPI core;
    private final TeaCorePlugin plugin;

    public DashboardApi(TeaCoreAPI core, TeaCorePlugin plugin) {
        this.core = core;
        this.plugin = plugin;
    }

    public void register(Javalin app) {
        app.get("/api/games", this::listGames);
        app.get("/api/arenas", this::listArenas);
        app.get("/api/arenas/{arenaId}", this::getArena);
        app.get("/api/queues", this::listQueues);
        app.get("/api/queues/{gameId}", this::getQueue);
        app.get("/api/maps", this::listAllMaps);
        app.get("/api/maps/{gameId}", this::listMapsForGame);
        app.get("/api/players/online", this::onlinePlayers);
        app.get("/api/players/{uuid}", this::getPlayer);
        app.get("/api/stats/{gameId}/{uuid}", this::getStatsForPlayer);

        // Control plane: every action publishes a ControlCommand to the arena's
        // owning server via the coordination backend. Responses are 202 Accepted -
        // the dashboard watches the event stream for confirmation.
        app.post("/api/arenas", this::allocateArena);
        app.post("/api/arenas/{arenaId}/start", this::forceStartArena);
        app.post("/api/arenas/{arenaId}/release", this::releaseArena);
        app.post("/api/arenas/{arenaId}/end-match", this::endArenaMatch);
        app.delete("/api/queues/{gameId}/{playerUuid}", this::dequeuePlayer);
        app.post("/api/maps/reload", this::reloadMaps);
        app.delete("/api/maps/{gameId}/{mapId}", this::deleteMap);
    }

    // ---- Games -----------------------------------------------------------

    private void listGames(Context ctx) {
        List<GameSummaryDto> games = new ArrayList<>();
        for (GameDefinition def : core.games().all()) {
            games.add(toDto(def));
        }
        ctx.json(games);
    }

    private GameSummaryDto toDto(GameDefinition def) {
        String selection = switch (def.mapSelection()) {
            case MapSelectionPolicy.Random ignored -> "random";
            case MapSelectionPolicy.Fixed fixed -> "fixed:" + fixed.mapId();
        };
        int mapCount = core.maps().forGame(def.id()).size();
        return new GameSummaryDto(
                def.id().value(),
                def.displayNameKey(),
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
                mapCount);
    }

    // ---- Arenas (distributed) -------------------------------------------

    private void listArenas(Context ctx) {
        List<ArenaDto> out = new ArrayList<>();
        for (ArenaSnapshot snapshot : core.coordination().allArenas()) {
            out.add(toDto(snapshot));
        }
        out.sort(Comparator.comparing(ArenaDto::gameId).thenComparing(ArenaDto::id));
        ctx.json(out);
    }

    private void getArena(Context ctx) {
        ArenaId arenaId = parseArenaId(ctx);
        if (arenaId == null) return;
        for (ArenaSnapshot snapshot : core.coordination().allArenas()) {
            if (snapshot.arenaId().equals(arenaId)) {
                ctx.json(toDto(snapshot));
                return;
            }
        }
        ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "arena not found"));
    }

    private ArenaDto toDto(ArenaSnapshot snapshot) {
        String mapId = snapshot.mapId() == null || snapshot.mapId().isEmpty() ? null : snapshot.mapId();
        // If the snapshot is from THIS server we can enrich from the local manager.
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
        return new ArenaDto(
                snapshot.arenaId().value().toString(),
                snapshot.gameId().value(),
                snapshot.serverId(),
                mapId,
                snapshot.state().name(),
                null,
                -1,
                snapshot.minPlayers(),
                snapshot.maxPlayers(),
                List.of(),
                List.of(),
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

    // ---- Queues ----------------------------------------------------------

    private void listQueues(Context ctx) {
        Map<String, List<QueueEntryDto>> out = new LinkedHashMap<>();
        CoordinationBackend backend = core.coordination();
        for (GameDefinition def : core.games().all()) {
            List<QueueEntryDto> entries = queueEntriesFor(backend, def.id());
            if (!entries.isEmpty()) out.put(def.id().value(), entries);
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

    // ---- Maps ------------------------------------------------------------

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

    // ---- Players ---------------------------------------------------------

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

        // Iterate persisted schemas (fleet-wide) and resolve to either the
        // in-process provider or a generic DB-backed one. Lets the lobby's
        // dashboard surface every game in the fleet, not only games whose
        // plugin happens to be loaded on this server.
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

    // ---- Stats -----------------------------------------------------------

    private void getStatsForPlayer(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid uuid"));
            return;
        }
        // Prefer the in-process provider; fall back to a schema-driven generic
        // provider so this works on a lobby with no game plugin loaded.
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

    // ---- Control plane (distributed) ------------------------------------

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
        core.coordination().publishControl(target, new ControlCommand(
                ControlCommand.Type.ALLOCATE_ARENA,
                null, body.gameId, body.mapId, null));
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
        core.coordination().publishControl(snapshot.serverId(), new ControlCommand(
                ControlCommand.Type.END_MATCH,
                arenaId.value().toString(), null, null, body.winnerUuid));
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
        core.coordination().publishControl(snapshot.serverId(), new ControlCommand(
                type, arenaId.value().toString(), null, null, null));
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
        // Queue lives in the coordination backend - safe to mutate from the
        // Jetty worker thread.
        core.coordination().dequeue(uuid);
        ctx.json(Map.of("ok", true));
    }

    private void reloadMaps(Context ctx) {
        // Fan out to every backend currently publishing arenas, plus ourselves.
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        targets.add(core.coordination().serverId());
        for (ArenaSnapshot s : core.coordination().allArenas()) targets.add(s.serverId());
        for (String target : targets) {
            core.coordination().publishControl(target, new ControlCommand(
                    ControlCommand.Type.RELOAD_MAPS, null, null, null, null));
        }
        ctx.json(Map.of("ok", true, "targets", targets));
    }

    private void deleteMap(Context ctx) {
        GameId gameId = new GameId(ctx.pathParam("gameId"));
        String mapId = ctx.pathParam("mapId");
        if (core.maps().get(gameId, mapId).isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "map not found"));
            return;
        }
        // Same fan-out rationale as reloadMaps: map definitions are per-plugin
        // in-memory state; every hosting server needs to drop the entry.
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        targets.add(core.coordination().serverId());
        for (ArenaSnapshot s : core.coordination().allArenas()) {
            if (s.gameId().equals(gameId)) targets.add(s.serverId());
        }
        for (String target : targets) {
            core.coordination().publishControl(target, new ControlCommand(
                    ControlCommand.Type.DELETE_MAP, null, gameId.value(), mapId, null));
        }
        ctx.json(Map.of("ok", true, "targets", targets));
    }

    // ---- helpers ---------------------------------------------------------

    @Nullable
    private ArenaId parseArenaId(Context ctx) {
        try {
            return new ArenaId(UUID.fromString(ctx.pathParam("arenaId")));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "invalid arena id"));
            return null;
        }
    }

    @Nullable
    private ArenaSnapshot findSnapshot(ArenaId arenaId) {
        for (ArenaSnapshot s : core.coordination().allArenas()) {
            if (s.arenaId().equals(arenaId)) return s;
        }
        return null;
    }

    /**
     * Picks a backend to send an ALLOCATE_ARENA command to. Preference order:
     * the local server (if it registered the game), then any server that has
     * at least one arena for this game currently published.
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

    /** Jackson-deserialized body for {@code POST /api/arenas}. */
    public static final class AllocateBody {
        public String gameId;
        @Nullable public String mapId;
        /** If omitted, the server chooses a target based on game registration. */
        @Nullable public String serverId;
    }

    /** Jackson-deserialized body for {@code POST /api/arenas/{id}/end-match}. */
    public static final class EndMatchBody {
        @Nullable public String winnerUuid;
    }
}
