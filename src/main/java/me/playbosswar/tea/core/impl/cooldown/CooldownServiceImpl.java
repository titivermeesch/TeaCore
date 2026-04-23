package me.playbosswar.tea.core.impl.cooldown;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.cooldown.CooldownDisplay;
import me.playbosswar.tea.core.api.cooldown.CooldownService;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CooldownServiceImpl implements CooldownService, Listener {

    private static final class Entry {
        final String label;
        final Instant startedAt;
        final Instant endsAt;
        final CooldownDisplay display;
        final float previousExp;
        final int previousLevel;

        Entry(String label, Instant startedAt, Instant endsAt, CooldownDisplay display,
                float previousExp, int previousLevel) {
            this.label = label;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.display = display;
            this.previousExp = previousExp;
            this.previousLevel = previousLevel;
        }
    }

    private final Plugin plugin;
    private final BukkitAudiences audiences;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Entry>> active = new ConcurrentHashMap<>();
    private BukkitTask task;

    public CooldownServiceImpl(Plugin plugin) {
        this.plugin = plugin;
        this.audiences = BukkitAudiences.create(plugin);
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        audiences.close();
    }

    @Override
    public void start(Player player, String id, String label, Duration duration, CooldownDisplay display) {
        Map<String, Entry> perPlayer = active.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>());
        perPlayer.put(id, new Entry(
                label == null ? id : label,
                Instant.now(),
                Instant.now().plus(duration),
                display,
                player.getExp(),
                player.getLevel()));
    }

    @Override
    public boolean isActive(Player player, String id) {
        Map<String, Entry> perPlayer = active.get(player.getUniqueId());
        if (perPlayer == null)
            return false;
        Entry e = perPlayer.get(id);
        if (e == null)
            return false;
        if (Instant.now().isAfter(e.endsAt)) {
            perPlayer.remove(id);
            return false;
        }
        return true;
    }

    @Override
    public Duration remaining(Player player, String id) {
        Map<String, Entry> perPlayer = active.get(player.getUniqueId());
        if (perPlayer == null)
            return Duration.ZERO;
        Entry e = perPlayer.get(id);
        if (e == null)
            return Duration.ZERO;
        Duration d = Duration.between(Instant.now(), e.endsAt);
        return d.isNegative() ? Duration.ZERO : d;
    }

    @Override
    public void cancelAll(Player player) {
        Map<String, Entry> perPlayer = active.remove(player.getUniqueId());
        if (perPlayer == null)
            return;
        restoreXp(player, perPlayer);
    }

    private void tick() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, ConcurrentHashMap<String, Entry>> pe : active.entrySet()) {
            Player p = Bukkit.getPlayer(pe.getKey());
            if (p == null)
                continue;
            ConcurrentHashMap<String, Entry> perPlayer = pe.getValue();

            perPlayer.entrySet().removeIf(e -> now.isAfter(e.getValue().endsAt));

            Entry soonestXp = soonest(perPlayer, CooldownDisplay.XP_BAR, now);
            if (soonestXp == null) {
                restoreXp(p, perPlayer);
            } else {
                renderXp(p, soonestXp, now);
            }

            Entry soonestActionBar = soonest(perPlayer, CooldownDisplay.ACTION_BAR, now);
            if (soonestActionBar != null) {
                renderActionBar(p, soonestActionBar, now);
            }
        }
    }

    private Entry soonest(Map<String, Entry> entries, CooldownDisplay display, Instant now) {
        Entry soonest = null;
        long soonestMs = Long.MAX_VALUE;
        for (Entry entry : entries.values()) {
            if (entry.display != display)
                continue;
            long ms = Duration.between(now, entry.endsAt).toMillis();
            if (ms < soonestMs) {
                soonestMs = ms;
                soonest = entry;
            }
        }
        return soonest;
    }

    private void renderXp(Player player, Entry entry, Instant now) {
        long totalMs = Duration.between(entry.startedAt, entry.endsAt).toMillis();
        long remainingMs = Math.max(0, Duration.between(now, entry.endsAt).toMillis());
        long elapsedMs = Math.max(1, totalMs - remainingMs);
        float progress = Math.max(0f, Math.min(1f, (float) elapsedMs / (float) Math.max(1, totalMs)));
        int secondsLeft = (int) Math.ceil(remainingMs / 1000.0);
        player.setLevel(secondsLeft);
        player.setExp(progress);
    }

    private void renderActionBar(Player player, Entry entry, Instant now) {
        long remainingMs = Math.max(0, Duration.between(now, entry.endsAt).toMillis());
        double seconds = remainingMs / 1000.0;
        Component component = Component.text(entry.label, NamedTextColor.AQUA)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.1fs", seconds), NamedTextColor.GOLD));
        audiences.player(player).sendActionBar(component);
    }

    private void restoreXp(Player player, Map<String, Entry> perPlayer) {
        Entry sample = perPlayer.values().stream().findFirst().orElse(null);
        if (sample != null) {
            player.setLevel(sample.previousLevel);
            player.setExp(sample.previousExp);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancelAll(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }
}
