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
 * Commands arrive on a Lettuce pub/sub thread, so Bukkit mutations are
 * scheduled on the main thread.
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
                plugin.arenaAdmin().endLocal(id, cmd.teamId(), cmd.playerUuid());
            }
            case KICK_PLAYER -> {
                UUID player = parseUuid(cmd.playerUuid());
                if (player != null) plugin.arenaAdmin().kickLocal(player);
            }
            case MAKE_SPECTATOR -> {
                ArenaId id = parseArena(cmd);
                UUID player = parseUuid(cmd.playerUuid());
                if (id != null && player != null) plugin.arenaAdmin().makeSpectatorLocal(id, player);
            }
            case MOVE_PLAYER -> {
                UUID player = parseUuid(cmd.playerUuid());
                if (player != null && cmd.targetArenaId() != null) {
                    plugin.arenaAdmin().moveLocal(player, cmd.targetArenaId());
                }
            }
            case EXPECT_PLAYER -> {
                ArenaId id = parseArena(cmd);
                UUID player = parseUuid(cmd.playerUuid());
                if (id != null && player != null && arenas.get(id).isPresent()) {
                    plugin.arenaAdmin().expect(player, id, cmd.mode());
                }
            }
            case APPLY_SETTINGS -> {
                if (cmd.mode() != null && plugin.settingsService() != null) {
                    plugin.settingsService().applyRemote(cmd.mode());
                }
            }
            case ALLOCATE_ARENA -> {
                if (cmd.gameId() == null || cmd.gameId().isBlank()) return;
                GameId gameId = new GameId(cmd.gameId());
                if (plugin.games().get(gameId).isEmpty()) {
                    logger.warning("ALLOCATE_ARENA for unknown game " + gameId.value());
                    return;
                }
                // No maps is normal before setup; log at FINE so retries don't spam.
                if (plugin.maps().forGame(gameId).isEmpty()) {
                    logger.fine(() -> "ALLOCATE_ARENA for " + gameId.value()
                            + " skipped: no maps authored on this backend.");
                    return;
                }
                ((me.playbosswar.tea.core.impl.arena.ArenaManagerImpl) arenas).allocateSoon(gameId, cmd.mapId());
            }
            case RELOAD_MAPS -> plugin.reloadMaps();
            case RESTORE_MAP -> {
                if (cmd.gameId() == null || cmd.mapId() == null || cmd.mode() == null) return;
                try {
                    plugin.mapEditingService().restore(new GameId(cmd.gameId()), cmd.mapId(),
                            Integer.parseInt(cmd.mode()), "dashboard");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to restore map " + cmd.gameId() + "/" + cmd.mapId()
                            + " to version " + cmd.mode() + ": " + e.getMessage());
                }
            }
            case DELETE_MAP -> {
                if (cmd.gameId() == null || cmd.mapId() == null) return;
                try {
                    plugin.mapEditingService().delete(new GameId(cmd.gameId()), cmd.mapId(), "dashboard");
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to delete map " + cmd.gameId() + "/" + cmd.mapId(), e);
                }
            }
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
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
