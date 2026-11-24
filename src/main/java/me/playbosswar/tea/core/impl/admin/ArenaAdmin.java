package me.playbosswar.tea.core.impl.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.team.TeamId;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.arena.ArenaManagerImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Each action runs locally when this server owns the arena, else it is published
 * as a {@link ControlCommand} to the owner. When the admin or a moved player must be
 * on the other server, an {@link ControlCommand.Type#EXPECT_PLAYER} is sent first and
 * they are moved through the proxy; the target server applies it when they arrive.
 */
public final class ArenaAdmin implements Listener {

    private static final Duration ARRIVAL_WINDOW = Duration.ofSeconds(30);

    private final TeaCorePlugin plugin;
    private final Map<UUID, Arrival> arrivals = new ConcurrentHashMap<>();

    private record Arrival(ArenaId arena, String mode, Instant until) { }

    public ArenaAdmin(TeaCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Matches the full arena id or a unique prefix of it. */
    public Optional<ArenaSnapshot> find(String idOrPrefix) {
        String needle = idOrPrefix.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return Optional.empty();
        }
        ArenaSnapshot match = null;
        for (ArenaSnapshot s : plugin.coordination().allArenas()) {
            String id = s.arenaId().value().toString();
            if (id.equals(needle)) {
                return Optional.of(s);
            }
            if (id.startsWith(needle)) {
                if (match != null) {
                    return Optional.empty();
                }
                match = s;
            }
        }
        // A brand new local arena may not be published yet.
        if (match == null) {
            for (Arena a : plugin.arenas().all()) {
                if (a.id().value().toString().startsWith(needle)) {
                    return Optional.of(localSnapshot(a));
                }
            }
        }
        return Optional.ofNullable(match);
    }

    private ArenaSnapshot localSnapshot(Arena a) {
        return new ArenaSnapshot(plugin.coordination().serverId(), a.id(), a.gameId(), a.mapId().orElse(""),
                a.state(), a.players().size(), a.minPlayers(), a.maxPlayers(), Instant.now());
    }

    public boolean isLocal(ArenaSnapshot s) {
        return s.serverId().equals(plugin.coordination().serverId()) && plugin.arenas().get(s.arenaId()).isPresent();
    }

    private void publish(ArenaSnapshot s, ControlCommand cmd) {
        plugin.coordination().publishControl(s.serverId(), cmd);
    }

    private static ControlCommand cmd(ControlCommand.Type type, ArenaSnapshot s, String player, String team,
                                      String target, String mode) {
        return new ControlCommand(type, s.arenaId().value().toString(), s.gameId().value(), null, player, team,
                target, mode);
    }

    public void start(ArenaSnapshot s) {
        if (isLocal(s)) {
            plugin.arenas().forceStart(s.arenaId());
        } else {
            publish(s, cmd(ControlCommand.Type.START_ARENA, s, null, null, null, null));
        }
    }

    public void release(ArenaSnapshot s) {
        if (isLocal(s)) {
            plugin.arenas().release(s.arenaId());
        } else {
            publish(s, cmd(ControlCommand.Type.RELEASE_ARENA, s, null, null, null, null));
        }
    }

    public void end(ArenaSnapshot s, String team, String player) {
        if (isLocal(s)) {
            endLocal(s.arenaId(), team, player);
        } else {
            publish(s, cmd(ControlCommand.Type.END_MATCH, s, player, team, null, null));
        }
    }

    public void endLocal(ArenaId id, String team, String player) {
        plugin.arenas().get(id).ifPresent(arena ->
                plugin.matchBroadcaster().toMatch(arena, "core.arena.admin.ended-broadcast"));
        if (team != null && !team.isBlank()) {
            plugin.arenas().endMatchForTeam(id, TeamId.of(team));
            return;
        }
        UUID winner = null;
        if (player != null && !player.isBlank()) {
            try {
                winner = UUID.fromString(player);
            } catch (IllegalArgumentException ignored) {
                // draw
            }
        }
        plugin.arenas().endMatch(id, winner);
    }

    public boolean allocate(GameId game, String mapId) {
        String target = allocationTarget(game);
        if (target == null) {
            return false;
        }
        if (target.equals(plugin.coordination().serverId())) {
            plugin.arenas().allocate(game, mapId == null || mapId.isBlank() ? null : mapId);
        } else {
            plugin.coordination().publishControl(target,
                    ControlCommand.of(ControlCommand.Type.ALLOCATE_ARENA, null, game.value(), mapId, null));
        }
        return true;
    }

    private String allocationTarget(GameId game) {
        if (plugin.games().get(game).isPresent()) {
            return plugin.coordination().serverId();
        }
        try {
            for (String host : plugin.coordination().hostsOf(game)) {
                return host;
            }
        } catch (RuntimeException ignored) {
            // backend without hosted-games tracking
        }
        for (ArenaSnapshot s : plugin.coordination().allArenas()) {
            if (s.gameId().equals(game)) {
                return s.serverId();
            }
        }
        return null;
    }

    /**
     * {@code mode}: {@code observe} (invisible, not a participant), {@code spectate}
     * or {@code tp:<player uuid>} (observe, then teleport to that player).
     */
    public void visit(Player admin, ArenaSnapshot s, String mode) {
        if (isLocal(s)) {
            applyArrival(admin, s.arenaId(), mode);
            return;
        }
        publish(s, cmd(ControlCommand.Type.EXPECT_PLAYER, s, admin.getUniqueId().toString(), null, null, mode));
        plugin.messageService().send(admin, "core.arena.admin.sending", Map.of("server", s.serverId()));
        plugin.proxyTransport().connect(admin, s.serverId());
    }

    /** Runs on the arena's server. */
    public void expect(UUID player, ArenaId arena, String mode) {
        arrivals.put(player, new Arrival(arena, mode == null ? "observe" : mode, Instant.now().plus(ARRIVAL_WINDOW)));
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            // Already here (same server, or the control message was slow).
            arrivals.remove(player);
            applyArrival(online, arena, mode);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Arrival arrival = arrivals.remove(player.getUniqueId());
        if (arrival == null || arrival.until().isBefore(Instant.now())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyArrival(player, arrival.arena(), arrival.mode());
            }
        }, 5L);
    }

    private void applyArrival(Player player, ArenaId arena, String mode) {
        String m = mode == null ? "observe" : mode;
        if (m.equals("join")) {
            plugin.arenas().addPlayer(arena, player, false);
        } else if (m.equals("spectate")) {
            plugin.arenas().addPlayer(arena, player, true);
        } else if (m.startsWith("tp:")) {
            plugin.arenas().observe(arena, player);
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(m.substring(3)));
                if (target != null) {
                    player.teleport(target);
                }
            } catch (IllegalArgumentException ignored) {
                // just observe
            }
        } else {
            plugin.arenas().observe(arena, player);
        }
    }

    public void kick(ArenaSnapshot s, UUID player) {
        if (isLocal(s)) {
            kickLocal(player);
        } else {
            publish(s, cmd(ControlCommand.Type.KICK_PLAYER, s, player.toString(), null, null, null));
        }
    }

    public void kickLocal(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && plugin.arenas().removePlayer(player, ArenaPlayerLeaveEvent.LeaveReason.KICK)) {
            plugin.messageService().send(player, "core.arena.admin.kicked-notice");
        }
    }

    public void makeSpectator(ArenaSnapshot s, UUID player) {
        if (isLocal(s)) {
            makeSpectatorLocal(s.arenaId(), player);
        } else {
            publish(s, cmd(ControlCommand.Type.MAKE_SPECTATOR, s, player.toString(), null, null, null));
        }
    }

    public void makeSpectatorLocal(ArenaId arena, UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && plugin.arenas() instanceof ArenaManagerImpl impl && impl.demoteToSpectator(arena, player)) {
            plugin.messageService().send(player, "core.arena.admin.spectator-notice");
        }
    }

    public void move(ArenaSnapshot from, UUID player, ArenaSnapshot to) {
        if (isLocal(from)) {
            moveLocal(player, to);
        } else {
            publish(from, new ControlCommand(ControlCommand.Type.MOVE_PLAYER, from.arenaId().value().toString(),
                    from.gameId().value(), null, player.toString(), null, to.arenaId().value().toString(), null));
        }
    }

    /** Runs on the server the player is on. */
    public void moveLocal(UUID uuid, ArenaSnapshot to) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        if (isLocal(to)) {
            // addPlayer leaves the current arena first.
            if (plugin.arenas().addPlayer(to.arenaId(), player, false)) {
                plugin.messageService().send(player, "core.arena.admin.moved-notice");
            }
            return;
        }
        // Leave without the lobby hop: the proxy moves them straight to the target.
        publish(to, cmd(ControlCommand.Type.EXPECT_PLAYER, to, uuid.toString(), null, null, "join"));
        plugin.messageService().send(player, "core.arena.admin.moved-notice");
        plugin.proxyTransport().connect(player, to.serverId());
    }

    public void moveLocal(UUID uuid, String targetArenaId) {
        find(targetArenaId).ifPresent(to -> moveLocal(uuid, to));
    }
}
