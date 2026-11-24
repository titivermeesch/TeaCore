package me.playbosswar.tea.core.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.arena.ArenaStateChangeEvent;
import me.playbosswar.tea.core.api.combat.ArenaKillEvent;
import me.playbosswar.tea.core.api.combat.CombatTracker;
import me.playbosswar.tea.core.api.combat.KillCredit;
import me.playbosswar.tea.core.api.combat.MatchStats;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.api.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class CombatTrackerImpl implements CombatTracker, Listener {

    /** A real death this soon after a reported one is the same death. */
    private static final long DUPLICATE_DEATH_MS = 1500L;

    private final ArenaManager arenas;
    private final TeamManager teams;
    private final LongSupplier clock;
    private final LongSupplier killWindowMs;
    private final LongSupplier assistWindowMs;

    /** victim -> (attacker -> epoch millis of their latest hit). */
    private final Map<UUID, Map<UUID, Long>> hits = new ConcurrentHashMap<>();
    /** arena -> player -> {kills, deaths, assists} for the current match. */
    private final Map<ArenaId, Map<UUID, int[]>> matchStats = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReported = new ConcurrentHashMap<>();

    /** Windows are read from {@code combat.*} on every use, so reloads apply. */
    public CombatTrackerImpl(Plugin plugin, ArenaManager arenas, TeamManager teams) {
        this(arenas, teams, System::currentTimeMillis,
                () -> Math.max(0L, plugin.getConfig().getLong("combat.kill-credit-window-ms", 6000L)),
                () -> Math.max(0L, plugin.getConfig().getLong("combat.assist-window-ms", 8000L)));
    }

    CombatTrackerImpl(ArenaManager arenas, TeamManager teams, LongSupplier clock,
                      LongSupplier killWindowMs, LongSupplier assistWindowMs) {
        this.arenas = arenas;
        this.teams = teams;
        this.clock = clock;
        this.killWindowMs = killWindowMs;
        this.assistWindowMs = assistWindowMs;
    }

    /** Test hook: records a hit at an explicit time, bypassing arena checks. */
    void putHit(UUID victim, UUID attacker, long at) {
        hits.computeIfAbsent(victim, k -> new ConcurrentHashMap<>()).put(attacker, at);
    }

    @Override
    public void recordHit(Arena arena, UUID victim, UUID attacker) {
        if (arena == null || victim == null || attacker == null || victim.equals(attacker)
                || arena.state() != ArenaState.LIVE
                || !arena.players().contains(victim) || !arena.players().contains(attacker)
                || sameTeam(arena, victim, attacker)) {
            return;
        }
        hits.computeIfAbsent(victim, k -> new ConcurrentHashMap<>()).put(attacker, clock.getAsLong());
    }

    @Override
    public KillCredit reportKill(Arena arena, Player victim, EntityDamageEvent.@Nullable DamageCause cause) {
        long now = clock.getAsLong();
        UUID victimId = victim.getUniqueId();
        KillCredit credit = resolve(victimId, cause, now);
        clear(victimId);
        lastReported.put(victimId, now);
        countMatch(arena.id(), credit);
        Bukkit.getPluginManager().callEvent(new ArenaKillEvent(arena, credit));
        return credit;
    }

    @Override
    public KillCredit reportKill(Arena arena, Player victim, EntityDamageEvent finalBlow) {
        if (finalBlow instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attackerOf(byEntity.getDamager());
            if (attacker != null) {
                recordHit(arena, victim.getUniqueId(), attacker.getUniqueId());
            }
        }
        return reportKill(arena, victim, finalBlow.getCause());
    }

    @Override
    public Optional<UUID> lastAttacker(UUID victim) {
        return Optional.ofNullable(resolve(victim, null, clock.getAsLong()).killer());
    }

    @Override
    public MatchStats matchStats(ArenaId arena, UUID player) {
        int[] line = matchStats.getOrDefault(arena, Map.of()).get(player);
        return line == null ? MatchStats.ZERO : new MatchStats(line[0], line[1], line[2]);
    }

    private void countMatch(ArenaId arena, KillCredit credit) {
        Map<UUID, int[]> perPlayer = matchStats.computeIfAbsent(arena, k -> new ConcurrentHashMap<>());
        perPlayer.computeIfAbsent(credit.victim(), k -> new int[3])[1]++;
        if (credit.killer() != null) {
            perPlayer.computeIfAbsent(credit.killer(), k -> new int[3])[0]++;
        }
        for (UUID assister : credit.assists()) {
            perPlayer.computeIfAbsent(assister, k -> new int[3])[2]++;
        }
    }

    @Override
    public void clear(UUID victim) {
        hits.remove(victim);
    }

    KillCredit resolve(UUID victim, EntityDamageEvent.@Nullable DamageCause cause, long now) {
        Map<UUID, Long> history = hits.getOrDefault(victim, Map.of());
        List<Map.Entry<UUID, Long>> recent = new ArrayList<>(new HashMap<>(history).entrySet());
        recent.sort(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()));
        UUID killer = null;
        List<UUID> assists = new ArrayList<>();
        for (Map.Entry<UUID, Long> hit : recent) {
            long age = now - hit.getValue();
            if (killer == null && age <= killWindowMs.getAsLong()) {
                killer = hit.getKey();
            } else if (age <= assistWindowMs.getAsLong()) {
                assists.add(hit.getKey());
            }
        }
        return new KillCredit(victim, killer, assists, cause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null) {
            return;
        }
        arenas.arenaOf(victim.getUniqueId()).ifPresent(arena ->
                recordHit(arena, victim.getUniqueId(), attacker.getUniqueId()));
    }

    /** Real deaths in a live arena are reported unless the game already did. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Arena arena = arenas.arenaOf(victim.getUniqueId()).orElse(null);
        if (arena == null || arena.state() != ArenaState.LIVE) {
            return;
        }
        Long reported = lastReported.get(victim.getUniqueId());
        if (reported != null && clock.getAsLong() - reported < DUPLICATE_DEATH_MS) {
            return;
        }
        EntityDamageEvent last = victim.getLastDamageCause();
        reportKill(arena, victim, last == null ? null : last.getCause());
    }

    @EventHandler
    public void onLeave(ArenaPlayerLeaveEvent event) {
        UUID left = event.player().getUniqueId();
        clear(left);
        lastReported.remove(left);
        hits.values().forEach(history -> history.remove(left));
    }

    @EventHandler
    public void onArenaState(ArenaStateChangeEvent event) {
        if (event.to() == ArenaState.ENDING || event.to() == ArenaState.RESETTING
                || event.to() == ArenaState.RELEASED) {
            for (UUID uuid : event.arena().players()) {
                clear(uuid);
                lastReported.remove(uuid);
            }
        }
        // Per-match totals stay readable while ENDING (summaries, sidebars) and
        // are dropped once the arena resets or a new match starts.
        if (event.to() == ArenaState.RESETTING || event.to() == ArenaState.RELEASED
                || event.to() == ArenaState.STARTING) {
            matchStats.remove(event.arena().id());
        }
    }

    private static @Nullable Player attackerOf(Entity damager) {
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().entities().playerBehind(damager);
    }

    private boolean sameTeam(Arena arena, UUID a, UUID b) {
        Optional<Team> ta = teams.teamOf(arena.id(), a);
        Optional<Team> tb = teams.teamOf(arena.id(), b);
        return ta.isPresent() && tb.isPresent() && ta.get().id().equals(tb.get().id());
    }
}
