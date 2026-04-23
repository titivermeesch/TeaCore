package me.playbosswar.tea.core.impl.coordination;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shows a persistent bossbar to each player currently in a queue, with their
 * position and the queue length. Refreshes every second by scanning
 * {@link CoordinationBackend#queueFor}.
 */
public final class QueueBossbarService implements Listener {

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final GameRegistry gameRegistry;
    private final MessageService messageService;
    private final ConcurrentHashMap<UUID, BossBar> bossbars = new ConcurrentHashMap<>();
    private BukkitTask task;

    public QueueBossbarService(Plugin plugin, CoordinationBackend backend,
            GameRegistry gameRegistry, MessageService messageService) {
        this.plugin = plugin;
        this.backend = backend;
        this.gameRegistry = gameRegistry;
        this.messageService = messageService;
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (BossBar bar : bossbars.values())
            bar.removeAll();
        bossbars.clear();
    }

    private void tick() {
        Set<UUID> stillQueued = new HashSet<>();
        for (GameDefinition def : gameRegistry.all()) {
            List<QueueEntry> entries = new ArrayList<>(backend.queueFor(def.id()));
            int total = entries.size();
            int pos = 0;
            for (QueueEntry entry : entries) {
                Player p = Bukkit.getPlayer(entry.playerUuid());
                if (p != null) {
                    updateBossbar(p, def.id().value(), pos + 1, total);
                    stillQueued.add(p.getUniqueId());
                }
                pos++;
            }
        }
        // Remove bossbars for players no longer in any queue
        bossbars.entrySet().removeIf(e -> {
            if (!stillQueued.contains(e.getKey())) {
                e.getValue().removeAll();
                return true;
            }
            return false;
        });
    }

    private void updateBossbar(Player player, String gameId, int position, int total) {
        BossBar bar = bossbars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar created = Bukkit.createBossBar(" ", BarColor.BLUE, BarStyle.SOLID);
            created.addPlayer(player);
            return created;
        });
        String locale = messageService.resolveLocale(player);
        String title = messageService.renderLegacy(locale, "core.queue.bossbar", Map.of(
                "game", gameId,
                "position", String.valueOf(position),
                "total", String.valueOf(total)));
        bar.setTitle(title);
        // Progress: 1.0 when you're #1, tapers as you're further back.
        double progress = total <= 1 ? 1.0 : Math.max(0.05, 1.0 - ((double) (position - 1) / total));
        bar.setProgress(progress);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar bar = bossbars.remove(event.getPlayer().getUniqueId());
        if (bar != null)
            bar.removeAll();
    }
}
