package me.playbosswar.tea.core.api.combat;

import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Shared damage history and kill credit for players in live arenas.
 *
 * <p>
 * TeaCore records every enemy hit on a participant automatically (melee and
 * projectiles, credited to the projectile's shooter; teammates are ignored).
 * Games add hits the server cannot attribute on its own, such as poison, a
 * pull into an explosion or custom ability damage, with
 * {@link #recordHit(Arena, UUID, UUID)}.
 *
 * <p>
 * When a participant dies, the game calls {@code reportKill} (games that fake
 * deaths by cancelling lethal damage must do this, passing the cancelled event so
 * the killing blow counts; a real
 * {@code PlayerDeathEvent} inside a live arena is reported automatically). The
 * tracker resolves the {@link KillCredit} - the most recent enemy hit within
 * {@code combat.kill-credit-window-ms} is the killer, every other enemy that hit
 * the victim within {@code combat.assist-window-ms} gets an assist - fires an
 * {@link ArenaKillEvent} and clears the victim's history. Core stats count
 * kills, deaths and assists from that event.
 */
public interface CombatTracker {

    /**
     * Records that {@code attacker} hit {@code victim} just now. Ignored unless
     * both are participants of {@code arena}, the arena is live, and they are on
     * different teams. Self-hits are ignored.
     *
     * @param arena    the arena both players are in.
     * @param victim   the player who was hit.
     * @param attacker the player credited with the hit.
     */
    void recordHit(Arena arena, UUID victim, UUID attacker);

    /**
     * Resolves and announces a participant's death: picks the killer and
     * assisters from the recent hit history, fires {@link ArenaKillEvent} and
     * clears the victim's history. Call it once per death.
     *
     * @param arena  the arena the victim died in.
     * @param victim the player who died.
     * @param cause  the damage cause of the final blow, or {@code null} if unknown.
     * @return the resolved credit (killer may be absent for environmental deaths).
     */
    KillCredit reportKill(Arena arena, Player victim, EntityDamageEvent.@Nullable DamageCause cause);

    /**
     * Like {@link #reportKill(Arena, Player, EntityDamageEvent.DamageCause)} for
     * a game that fakes deaths by cancelling the lethal damage event: the
     * attacker behind {@code finalBlow} (a player, or the shooter of a
     * projectile, TNT or effect cloud) is counted as the latest hit first, since
     * a cancelled event is never recorded on its own.
     *
     * @param arena     the arena the victim died in.
     * @param victim    the player who died.
     * @param finalBlow the (usually cancelled) damage event that would have killed them.
     * @return the resolved credit.
     */
    KillCredit reportKill(Arena arena, Player victim, EntityDamageEvent finalBlow);

    /**
     * The most recent enemy who hit {@code victim} within the kill-credit window.
     *
     * @param victim the player to look up.
     * @return the attacker, or empty if nobody hit them recently.
     */
    Optional<UUID> lastAttacker(UUID victim);

    /**
     * The player's kills, deaths and assists in the current match of an arena,
     * counted from {@link ArenaKillEvent}s. Kept until the arena resets.
     */
    MatchStats matchStats(me.playbosswar.tea.core.api.arena.ArenaId arena, UUID player);

    /** Forgets every recorded hit on {@code victim} (e.g. on respawn or leave). */
    void clear(UUID victim);
}
