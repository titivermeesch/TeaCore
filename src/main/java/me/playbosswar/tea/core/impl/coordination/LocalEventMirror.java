package me.playbosswar.tea.core.impl.coordination;

import me.playbosswar.tea.core.api.arena.ArenaPlayerJoinEvent;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.arena.MatchEndEvent;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class LocalEventMirror implements Listener {

    private final CoordinationBackend backend;

    public LocalEventMirror(CoordinationBackend backend) {
        this.backend = backend;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaState(ArenaStateChangeEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.ARENA_STATE,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                event.from().name(),
                event.to().name(),
                null, null, null, null, null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining(),
                null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(ArenaPlayerJoinEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.ARENA_PLAYER_JOIN,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null,
                event.player().getUniqueId().toString(),
                event.player().getName(),
                event.spectator(),
                null, null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining(),
                null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(ArenaPlayerLeaveEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.ARENA_PLAYER_LEAVE,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null,
                event.player().getUniqueId().toString(),
                event.player().getName(),
                null,
                event.reason().name(),
                null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining(),
                null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.MATCH_END,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null, null, null, null, null,
                event.winner().map(java.util.UUID::toString).orElse(null),
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining(),
                null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(me.playbosswar.tea.core.api.combat.ArenaKillEvent event) {
        me.playbosswar.tea.core.api.combat.KillCredit credit = event.credit();
        java.util.Map<String, String> data = new java.util.HashMap<>();
        credit.killerId().ifPresent(k -> {
            data.put("killer", k.toString());
            data.put("killerName", nameOf(k));
        });
        java.util.List<String> assists = new java.util.ArrayList<>();
        for (java.util.UUID a : credit.assists()) {
            assists.add(nameOf(a));
        }
        data.put("assists", String.join(",", assists));
        if (credit.cause() != null) {
            data.put("cause", credit.cause().name());
        }
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.KILL,
                backend.serverId(),
                event.arena().id().value().toString(),
                event.arena().gameId().value(),
                null, null,
                credit.victim().toString(),
                nameOf(credit.victim()),
                null, null, null,
                event.arena().players().size(),
                event.arena().phaseSecondsRemaining(),
                data));
    }

    // MONITOR so it runs after channel routing, which only narrows recipients.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        me.playbosswar.tea.core.api.arena.Arena arena;
        try {
            arena = me.playbosswar.tea.core.api.TeaCoreAPI.get().arenas()
                    .arenaOf(event.getPlayer().getUniqueId()).orElse(null);
        } catch (IllegalStateException e) {
            return;
        }
        if (arena == null) {
            return;
        }
        backend.publishEvent(new CoordinationEvent(
                CoordinationEvent.Type.CHAT,
                backend.serverId(),
                arena.id().value().toString(),
                arena.gameId().value(),
                null, null,
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                arena.spectators().contains(event.getPlayer().getUniqueId()),
                null, null,
                arena.players().size(),
                arena.phaseSecondsRemaining(),
                java.util.Map.of("message", event.getMessage())));
    }

    private static String nameOf(java.util.UUID uuid) {
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
        if (p != null) {
            return p.getName();
        }
        String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }
}
