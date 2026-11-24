package me.playbosswar.tea.core.impl.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.kit.KitSelectorGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class KitCommand implements TabExecutor {

    private final TeaCorePlugin plugin;
    private final KitSelectorGui gui;

    public KitCommand(TeaCorePlugin plugin, KitSelectorGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return true;
        }

        GameId gameId = resolveGameId(player, args);
        if (gameId == null) return true;
        if (plugin.games().get(gameId).isEmpty()) {
            plugin.messageService().send(player, "lobby.play.unknown-game",
                    Map.of("game", gameId.value()));
            return true;
        }

        if (args.length >= 2) {
            String kitId = args[1].toLowerCase(Locale.ROOT);
            Optional<Kit> kit = plugin.kits().kit(gameId, kitId);
            if (kit.isEmpty()) {
                plugin.messageService().send(player, "core.kit-picker.unknown",
                        Map.of("kit", kitId, "game", gameId.value()));
                return true;
            }
            // Same rules as the menu: priced kits are bought (or refused) first.
            plugin.coreMenus().selectKit(player, gameId, kitId);
            return true;
        }

        if (!plugin.menus().open(player, "kits", Map.of("game", gameId.value()))) {
            gui.open(player, gameId);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return plugin.games().all().stream()
                    .map(def -> def.id().value())
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            try {
                GameId g = GameId.of(args[0]);
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return plugin.kits().kitsFor(g).stream()
                        .map(Kit::kitId)
                        .filter(s -> s.startsWith(prefix))
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private GameId resolveGameId(Player player, String[] args) {
        if (args.length >= 1) {
            return GameId.of(args[0].toLowerCase(Locale.ROOT));
        }
        Arena arena = plugin.arenas().arenaOf(player.getUniqueId()).orElse(null);
        if (arena != null) return arena.gameId();
        plugin.messageService().send(player, "core.kit-picker.usage");
        return null;
    }
}
