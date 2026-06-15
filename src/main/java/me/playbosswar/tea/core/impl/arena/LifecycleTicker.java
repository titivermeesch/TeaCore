package me.playbosswar.tea.core.impl.arena;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Ticks every second. Drives arena state transitions based on time and player
 * count,
 * and pushes arenas from WAITING through to RELEASED if their game ends.
 */
public final class LifecycleTicker {

    private final Plugin plugin;
    private final ArenaManagerImpl arenaManager;
    private final GameRegistry gameRegistry;
    private final MessageService messageService;
    private BukkitTask task;

    public LifecycleTicker(Plugin plugin, ArenaManagerImpl arenaManager, GameRegistry gameRegistry,
            MessageService messageService) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
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
    }

    private void tick() {
        for (ArenaImpl arena : List.copyOf(arenaManager.arenasForTicker())) {
            GameDefinition def = gameRegistry.get(arena.gameId()).orElse(null);
            if (def == null)
                continue;
            switch (arena.state()) {
                case WAITING -> tickWaiting(arena, def);
                case COUNTDOWN -> tickCountdown(arena, def);
                case STARTING -> tickStarting(arena, def);
                case LIVE -> tickLive(arena, def);
                case ENDING -> tickEnding(arena, def);
                default -> {
                    /* ALLOCATING, RESETTING, RELEASED: no timer */ }
            }
        }
    }

    private void tickWaiting(ArenaImpl arena, GameDefinition def) {
        int minPlayers = arena.minPlayers();
        if (arena.players().size() >= minPlayers) {
            arenaManager.transition(arena, ArenaState.COUNTDOWN, def.lifecycle().countdownSeconds());
            return;
        }
        if (arena.phaseSecondsRemaining() > 0) {
            arena.decrementPhaseSecondsRemaining();
        }
    }

    private void tickCountdown(ArenaImpl arena, GameDefinition def) {
        int minPlayers = arena.minPlayers();
        if (minPlayers > 1 && arena.players().size() < minPlayers) {
            broadcastCountdownAbort(arena);
            arenaManager.transition(arena, ArenaState.WAITING, def.lifecycle().maxWaitSeconds());
            return;
        }
        int remaining = arena.phaseSecondsRemaining();
        if (remaining <= 0) {
            broadcastCountdownGo(arena);
            arenaManager.transition(arena, ArenaState.STARTING, def.lifecycle().startingSeconds());
            applyStarting(arena);
        } else {
            broadcastCountdownTick(arena, remaining, def);
            arena.decrementPhaseSecondsRemaining();
        }
    }

    private void broadcastCountdownTick(ArenaImpl arena, int secondsRemaining, GameDefinition def) {
        int titleThreshold = def.lifecycle().titleCountdownThreshold();
        if (secondsRemaining <= titleThreshold) {
            boolean plingTick = secondsRemaining <= 5;
            Map<String, String> placeholders = Map.of("seconds", String.valueOf(secondsRemaining));
            for (UUID uuid : arena.players()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null)
                    continue;
                messageService.showTitle(p, "core.countdown.title", "core.countdown.subtitle",
                        placeholders, 0, 25, 5);
                if (plingTick) {
                    float pitch = 1.0f + (6 - secondsRemaining) * 0.1f;
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
                }
            }
            return;
        }
        // Above the title threshold: emit a chat announcement only on the configured
        // second marks.
        if (def.lifecycle().chatAnnounceAtSeconds().contains(secondsRemaining)) {
            for (UUID uuid : arena.players()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null)
                    continue;
                messageService.send(p, "core.countdown.chat",
                        Map.of("seconds", String.valueOf(secondsRemaining)));
            }
        }
    }

    private void broadcastCountdownGo(ArenaImpl arena) {
        for (UUID uuid : arena.players()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null)
                continue;
            messageService.showTitle(p, "core.countdown.go.title", "core.countdown.go.subtitle",
                    0, 30, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }
    }

    private void broadcastCountdownAbort(ArenaImpl arena) {
        for (UUID uuid : arena.players()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null)
                continue;
            messageService.showTitle(p, "core.countdown.abort.title", "core.countdown.abort.subtitle",
                    5, 40, 10);
        }
    }

    private void tickStarting(ArenaImpl arena, GameDefinition def) {
        if (arena.phaseSecondsRemaining() <= 0) {
            int matchSeconds = def.lifecycle().maxMatchSeconds();
            arenaManager.transition(arena, ArenaState.LIVE, matchSeconds > 0 ? matchSeconds : -1);
        } else {
            arena.decrementPhaseSecondsRemaining();
        }
    }

    private void tickLive(ArenaImpl arena, GameDefinition def) {
        int remaining = arena.phaseSecondsRemaining();
        if (remaining < 0)
            return; // no timer configured
        if (remaining == 0) {
            // Fire once, then mark as fired by flipping to -1. Games decide the winner.
            arena.setPhaseSecondsRemaining(-1);
            Bukkit.getPluginManager().callEvent(
                    new me.playbosswar.tea.core.api.arena.MatchTimedOutEvent(arena));
            return;
        }
        arena.decrementPhaseSecondsRemaining();
    }

    private void tickEnding(ArenaImpl arena, GameDefinition def) {
        if (arena.phaseSecondsRemaining() <= 0) {
            finishArena(arena, def);
        } else {
            arena.decrementPhaseSecondsRemaining();
        }
    }

    private void applyStarting(ArenaImpl arena) {
        List<SpawnPoint> spawns = arenaManager.participantSpawns(arena);
        if (spawns.isEmpty() || arena.world().isEmpty())
            return;
        int i = 0;
        for (java.util.UUID uuid : arena.players()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null)
                continue;
            SpawnPoint s = spawns.get(i % spawns.size());
            p.teleport(s.toLocation(me.playbosswar.tea.core.impl.map.MapOrigin.in(arena.world().get())));
            i++;
        }
    }

    private void finishArena(ArenaImpl arena, GameDefinition def) {
        switch (def.endMode()) {
            case RESET -> {
                arenaManager.evacuate(arena);
                arenaManager.release(arena.id());
            }
            case SHUTDOWN -> {
                arenaManager.evacuate(arena);
                Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 100L);
            }
        }
    }
}
