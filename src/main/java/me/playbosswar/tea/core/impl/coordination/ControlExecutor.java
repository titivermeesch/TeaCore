package me.playbosswar.tea.core.impl.coordination;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import org.bukkit.Bukkit;

/**
 * Executes {@link ControlCommand}s received from the coordination backend
 * against the local {@link ArenaManager} / map services.
 *
 * <p>
 * Registered on every backend so the dashboard (running on the lobby) can reach
 * any arena-hosting pod by target {@code serverId}. Incoming commands arrive on
 * a Lettuce pub/sub thread; Bukkit mutations are scheduled on the main thread
 * with {@link org.bukkit.scheduler.BukkitScheduler#runTask}.
 */
public final class ControlExecutor {

    private final TeaCorePlugin plugin;
    private final CoordinationBackend backend;
    private final Logger logger;

    public ControlExecutor(TeaCorePlugin plugin, CoordinationBackend backend, Logger logger) {
        this.plugin = plugin;
        this.backend = backend;
        this.logger = logger;
    }

    public void start() {
        backend.subscribeControl(this::execute);
    }

    private void execute(ControlCommand cmd) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                dispatch(cmd);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Control command " + cmd.type() + " failed", e);
            }
        });
    }

    private void dispatch(ControlCommand cmd) {
        ArenaManager arenas = plugin.arenas();
        switch (cmd.type()) {
            case START_ARENA -> {
                ArenaId id = parseArena(cmd);
                if (id != null && arenas.get(id).isPresent()) arenas.forceStart(id);
            }
            case RELEASE_ARENA -> {
                ArenaId id = parseArena(cmd);
                if (id != null && arenas.get(id).isPresent()) arenas.release(id);
            }
            case END_MATCH -> {
                ArenaId id = parseArena(cmd);
                if (id == null || arenas.get(id).isEmpty()) return;
                UUID winner = null;
                if (cmd.playerUuid() != null && !cmd.playerUuid().isBlank()) {
                    try {
                        winner = UUID.fromString(cmd.playerUuid());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                arenas.endMatch(id, winner);
            }
            case ALLOCATE_ARENA -> {
                if (cmd.gameId() == null || cmd.gameId().isBlank()) return;
                GameId gameId = new GameId(cmd.gameId());
                if (plugin.games().get(gameId).isEmpty()) {
                    logger.warning("ALLOCATE_ARENA for unknown game " + gameId.value());
                    return;
                }
                // No maps registered is a normal pre-setup state; report at FINE so
                // we don't spam every retry while the operator is still authoring.
                if (plugin.maps().forGame(gameId).isEmpty()) {
                    logger.fine(() -> "ALLOCATE_ARENA for " + gameId.value()
                            + " skipped: no maps authored on this backend.");
                    return;
                }
                arenas.allocate(gameId, cmd.mapId());
            }
            case RELOAD_MAPS -> plugin.reloadMaps();
            case DELETE_MAP -> {
                if (cmd.gameId() == null || cmd.mapId() == null) return;
                try {
                    plugin.mapEditingService().delete(new GameId(cmd.gameId()), cmd.mapId());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to delete map " + cmd.gameId() + "/" + cmd.mapId(), e);
                }
            }
        }
    }

    private static ArenaId parseArena(ControlCommand cmd) {
        if (cmd.arenaId() == null) return null;
        try {
            return new ArenaId(UUID.fromString(cmd.arenaId()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
