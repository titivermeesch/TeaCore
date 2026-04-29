package me.playbosswar.tea.core.impl.lobby.command;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@code /play <game> [map]}. In standalone mode we go direct: find-or-allocate
 * local arena
 * and add the player. In multi-server/bungeecord mode we publish a queue entry;
 * the
 * leader-elected Matchmaker in TeaCore routes the player to the right backend.
 */
public final class PlayCommand implements TabExecutor {

    private final Plugin plugin;
    private final TeaCoreAPI core;

    public PlayCommand(Plugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messageService().sendToSender(sender, "core.command.player-only");
            return true;
        }
        if (args.length == 0) {
            core.messageService().send(player, "lobby.play.usage");
            return true;
        }

        GameId gameId = GameId.of(args[0].toLowerCase(Locale.ROOT));
        String mapFilter = args.length >= 2 ? args[1] : null;

        if (core.isStandalone()) {
            handleStandalone(player, gameId, mapFilter);
        } else {
            handleQueued(player, gameId, mapFilter);
        }
        return true;
    }

    private void handleStandalone(Player player, GameId gameId, String mapFilter) {
        Optional<GameDefinition> optDef = core.games().get(gameId);
        if (optDef.isEmpty()) {
            core.messageService().send(player, "lobby.play.unknown-game",
                    Map.of("game", gameId.value()));
            return;
        }

        Arena target = findWaitingArena(gameId, mapFilter)
                .orElseGet(() -> allocate(player, gameId, mapFilter));
        if (target == null)
            return;

        boolean joined = core.arenas().addPlayer(target.id(), player, false);
        if (joined) {
            core.messageService().send(player, "lobby.play.joined", Map.of(
                    "game", gameId.value(),
                    "arena", target.id().value().toString().substring(0, 8),
                    "map", target.mapId().orElse("-")));
        } else {
            core.messageService().send(player, "lobby.play.join-failed",
                    Map.of("game", gameId.value()));
        }
    }

    private void handleQueued(Player player, GameId gameId, String mapFilter) {
        List<String> mapIds = mapFilter == null ? List.of() : List.of(mapFilter);
        QueueEntry entry = new QueueEntry(
                player.getUniqueId(), player.getName(), gameId, mapIds, Instant.now());
        core.coordination().enqueue(entry);
        core.messageService().send(player, "lobby.play.queued", Map.of(
                "game", gameId.value(),
                "map", mapFilter == null ? "any" : mapFilter));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return core.games().all().stream()
                    .map(d -> d.id().value())
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            try {
                GameId g = GameId.of(args[0]);
                return core.maps().forGame(g).stream()
                        .map(m -> m.mapId())
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private Optional<Arena> findWaitingArena(GameId gameId, String mapFilter) {
        return core.arenas().all().stream()
                .filter(a -> a.gameId().equals(gameId))
                .filter(a -> a.state() == ArenaState.WAITING || a.state() == ArenaState.COUNTDOWN)
                .filter(a -> a.players().size() < a.maxPlayers())
                .filter(a -> mapFilter == null || a.mapId().map(mapFilter::equalsIgnoreCase).orElse(false))
                .findFirst()
                .map(a -> (Arena) a);
    }

    private Arena allocate(Player player, GameId gameId, String mapFilter) {
        try {
            return core.arenas().allocate(gameId, mapFilter);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to allocate arena for " + gameId + ": " + e.getMessage());
            core.messageService().send(player, "lobby.play.allocation-failed",
                    Map.of("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return null;
        }
    }
}
