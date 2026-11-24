package me.playbosswar.tea.core.api.combat;

import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Damage history and kill credit for players in live arenas.
 *
 * <p>
 * Melee and projectile hits between enemies are recorded automatically
 * (projectiles credit the shooter). Games record hits the server cannot
 * attribute, such as poison or ability damage, with
 * {@link #recordHit(Arena, UUID, UUID)}.
 *
 * <p>
 * A real {@code PlayerDeathEvent} in a live arena is reported automatically.
 * Games that fake deaths by cancelling lethal damage must call
 * {@link #reportKill(Arena, Player, EntityDamageEvent)} themselves. The killer
 * is the latest enemy hit within {@code combat.kill-credit-window-ms}; other
 * enemies that hit within {@code combat.assist-window-ms} get assists. Core
 * stats count kills, deaths and assists from the resulting {@link ArenaKillEvent}.
 */
public interface CombatTracker {

    /**
     * Ignored unless both players are participants of {@code arena}, the arena
     * is live, and they are on different teams. Self-hits are ignored.
     */
    void recordHit(Arena arena, UUID victim, UUID attacker);

    /**
     * Resolves killer and assists, fires {@link ArenaKillEvent} and clears the
     * victim's history. Call once per death.
     *
     * @param cause the final blow's cause, or {@code null} if unknown.
     * @return the credit; the killer is absent for environmental deaths.
     */
    KillCredit reportKill(Arena arena, Player victim, EntityDamageEvent.@Nullable DamageCause cause);

    /**
     * Variant for games that fake deaths by cancelling the lethal damage. The
     * attacker behind {@code finalBlow} (a player, or the source of a
     * projectile, TNT or effect cloud) is recorded as the latest hit first,
     * since cancelled events are never recorded.
     */
    KillCredit reportKill(Arena arena, Player victim, EntityDamageEvent finalBlow);

    /** The latest enemy hit within the kill-credit window, or empty. */
    Optional<UUID> lastAttacker(UUID victim);

    /** Counted from {@link ArenaKillEvent}s; kept until the arena resets. */
    MatchStats matchStats(me.playbosswar.tea.core.api.arena.ArenaId arena, UUID player);

    /**
     * Lets a cosmetics plugin announce kills with its own line. A game with its
     * own kill feed checks {@link #customKillMessage} and leaves the line to
     * that plugin when it returns one.
     *
     * @param provider the MiniMessage line for a killer (with {@code <killer>} and
     *                 {@code <victim>}), or {@code null} for the game's own line
     */
    default void setKillMessageProvider(java.util.function.@Nullable Function<UUID, @Nullable String> provider) {
    }

    default java.util.Optional<String> customKillMessage(UUID killer) {
        return java.util.Optional.empty();
    }

    /** Damage {@code player} dealt to other players in the current or just-ended match. */
    default double damageDealt(me.playbosswar.tea.core.api.arena.ArenaId arena, UUID player) {
        return 0;
    }

    /** Forgets every recorded hit on {@code victim} (e.g. on respawn or leave). */
    void clear(UUID victim);
}
