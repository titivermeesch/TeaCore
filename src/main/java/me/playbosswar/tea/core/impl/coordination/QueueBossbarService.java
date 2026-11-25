package me.playbosswar.tea.core.impl.coordination;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
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
 * Reads every queue from the {@link CoordinationBackend} off the main thread, so
 * it works on a lobby with no game plugin registered, then updates the bars on
 * the main thread.
 */
public final class QueueBossbarService implements Listener {

    private record Spot(GameId gameId, int position, int total) { }

    private final Plugin plugin;
    private final CoordinationBackend backend;
    private final MessageService messageService;
    // Adventure boss bars: native on 1.9+, emulated on 1.8 by the platform adapter.
    private final BukkitAudiences audiences;
    private final ConcurrentHashMap<UUID, BossBar> bossbars = new ConcurrentHashMap<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean enabled = true;
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
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::collect, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        bossbars.forEach(this::hide);
        bossbars.clear();
    }

    /** Async: one read per game with a queue instead of three per online player. */
    private void collect() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            Map<UUID, Spot> spots = new HashMap<>();
            if (enabled) {
                Set<GameId> gameIds = new HashSet<>(backend.allHostedGames());
                for (ArenaSnapshot snap : backend.allArenas()) {
                    gameIds.add(snap.gameId());
                }
                for (GameId gameId : gameIds) {
                    Collection<QueueEntry> queue = backend.queueFor(gameId);
                    int position = 0;
                    for (QueueEntry entry : queue) {
                        spots.putIfAbsent(entry.playerUuid(), new Spot(gameId, ++position, queue.size()));
                    }
                }
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> apply(spots));
            }
        } finally {
            busy.set(false);
        }
    }

    private void apply(Map<UUID, Spot> spots) {
        if (task == null) {
            return;
        }
        enabled = plugin.getConfig().getBoolean("features.queue-bossbar", true);
        Set<UUID> stillQueued = new HashSet<>();
        if (enabled) {
            spots.forEach((uuid, spot) -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    updateBossbar(p, spot.gameId().value(), spot.position(), spot.total());
                    stillQueued.add(uuid);
                }
            });
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
