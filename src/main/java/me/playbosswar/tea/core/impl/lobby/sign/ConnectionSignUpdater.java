package me.playbosswar.tea.core.impl.lobby.sign;

import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ConnectionSignUpdater {

    private final Plugin plugin;
    private final TeaCoreAPI core;
    private final ConnectionSignService signs;
    private BukkitTask task;

    public ConnectionSignUpdater(Plugin plugin, TeaCoreAPI core, ConnectionSignService signs) {
        this.plugin = plugin;
        this.core = core;
        this.signs = signs;
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (ConnectionSign sign : signs.all()) {
            World world = Bukkit.getWorld(sign.world());
            if (world == null)
                continue;
            if (!world.isChunkLoaded(sign.x() >> 4, sign.z() >> 4))
                continue;
            Block block = world.getBlockAt(sign.x(), sign.y(), sign.z());
            if (!(block.getState() instanceof Sign signState))
                continue;

            List<String> lines = renderLines(sign);
            // setLine writes the front side on 1.20+ and the only side before.
            for (int i = 0; i < lines.size() && i < 4; i++) {
                signState.setLine(i, lines.get(i));
            }
            signState.update();
        }
    }

    private List<String> renderLines(ConnectionSign sign) {
        MessageService ms = core.messageService();
        String defaultLocale = "en_US";

        int totalPlayers = 0;
        int availableArenas = 0;
        for (ArenaSnapshot snapshot : core.coordination().allArenas()) {
            if (!snapshot.gameId().equals(sign.gameId()))
                continue;
            if (!sign.acceptsAnyMap() && !sign.mapFilter().equalsIgnoreCase(snapshot.mapId()))
                continue;
            totalPlayers += snapshot.players();
            if (snapshot.isAvailable())
                availableArenas++;
        }

        String mapDisplay;
        if (sign.acceptsAnyMap()) {
            mapDisplay = "any";
        } else {
            mapDisplay = core.maps().get(sign.gameId(), sign.mapFilter())
                    .map(m -> m.resolvedDisplayName())
                    .orElse(sign.mapFilter());
        }
        Map<String, String> placeholders = Map.of(
                "game", sign.gameId().value(),
                "map", mapDisplay,
                "players", String.valueOf(totalPlayers),
                "arenas", String.valueOf(availableArenas));
        String line1 = ms.renderLegacy(defaultLocale, "lobby.sign.line1", placeholders);
        String line2 = ms.renderLegacy(defaultLocale, "lobby.sign.line2", placeholders);
        String line3 = ms.renderLegacy(defaultLocale, "lobby.sign.line3", placeholders);
        String line4 = ms.renderLegacy(defaultLocale, "lobby.sign.line4", placeholders);
        return List.of(line1, line2, line3, line4);
    }
}
