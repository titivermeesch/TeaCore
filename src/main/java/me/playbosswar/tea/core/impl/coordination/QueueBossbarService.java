package me.playbosswar.tea.core.impl.coordination;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Bukkit;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Asks the {@link CoordinationBackend} per online player, so it works on a lobby
 * with no game plugin registered.
 */
public final class QueueBossbarService implements Listener {

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final MessageService messageService;
    // Adventure boss bars: native on 1.9+, emulated on 1.8 by the platform adapter.
    private final BukkitAudiences audiences;
    private final ConcurrentHashMap<UUID, BossBar> bossbars = new ConcurrentHashMap<>();
    private BukkitTask task;

    public QueueBossbarService(Plugin plugin, CoordinationBackend backend,
            MessageService messageService, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.backend = backend;
        this.messageService = messageService;
        this.audiences = audiences;
    }

    private void hide(UUID uuid, BossBar bar) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            audiences.player(p).hideBossBar(bar);
        }
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
        bossbars.forEach(this::hide);
        bossbars.clear();
    }

    private void tick() {
        Set<UUID> stillQueued = new HashSet<>();
        boolean enabled = plugin.getConfig().getBoolean("features.queue-bossbar", true);
        for (Player p : enabled ? Bukkit.getOnlinePlayers() : java.util.List.<Player>of()) {
            QueueEntry entry = backend.queueEntryOf(p.getUniqueId()).orElse(null);
            if (entry == null)
                continue;
            GameId gameId = entry.gameId();
            int total = backend.queueFor(gameId).size();
            int position = backend.queuePosition(p.getUniqueId(), gameId);
            if (position < 0)
                continue; // entry vanished between the two lookups
            updateBossbar(p, gameId.value(), position + 1, total);
            stillQueued.add(p.getUniqueId());
        }
        bossbars.entrySet().removeIf(e -> {
            if (!stillQueued.contains(e.getKey())) {
                hide(e.getKey(), e.getValue());
                return true;
            }
            return false;
        });
    }

    private void updateBossbar(Player player, String gameId, int position, int total) {
        BossBar bar = bossbars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar created = BossBar.bossBar(net.kyori.adventure.text.Component.space(), 1f,
                    BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            audiences.player(player).showBossBar(created);
            return created;
        });
        String locale = messageService.resolveLocale(player);
        String title = messageService.renderLegacy(locale, "core.queue.bossbar", Map.of(
                "game", gameId,
                "position", String.valueOf(position),
                "total", String.valueOf(total)));
        bar.name(LegacyComponentSerializer.legacySection().deserialize(title));
        double progress = total <= 1 ? 1.0 : Math.max(0.05, 1.0 - ((double) (position - 1) / total));
        bar.progress((float) progress);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossbars.remove(event.getPlayer().getUniqueId());
    }
}
