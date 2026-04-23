package me.playbosswar.tea.core.api.kit;

import java.time.Duration;
import java.util.Map;
import me.playbosswar.tea.core.api.arena.Arena;
import org.bukkit.entity.Player;

/**
 * Runtime context supplied to an {@link Ability} when its trigger fires.
 *
 * <p>
 * Carries the invoking player, hosting arena, binding-specific config, and
 * helpers for
 * managing this ability's cooldown. Implementations are backed by the core
 * cooldown
 * service so cooldowns survive ability-state resets within the same binding.
 */
public interface AbilityContext {

    /**
     * Returns the player whose input triggered the ability.
     *
     * @return the player, never {@code null}.
     */
    Player player();

    /**
     * Returns the arena the player is currently in.
     *
     * @return the arena, never {@code null}.
     */
    Arena arena();

    /**
     * Returns the ability-specific configuration from the binding.
     *
     * <p>
     * Values are populated from the YAML {@code ability.config:} block and passed
     * through unchanged from {@link AbilityBinding#config()}.
     *
     * @return the configuration map, never {@code null}.
     */
    Map<String, Object> config();

    /**
     * Starts a cooldown on this ability for the current player.
     *
     * <p>
     * Subsequent triggers within the cooldown window are no-ops. The underlying
     * {@link me.playbosswar.tea.core.api.cooldown.CooldownService} is used, so the
     * configured {@link me.playbosswar.tea.core.api.cooldown.CooldownDisplay}
     * surface
     * applies.
     *
     * @param duration how long the cooldown lasts; must be positive.
     */
    void startCooldown(Duration duration);

    /**
     * Indicates whether this ability is currently on cooldown for the player.
     *
     * @return {@code true} if a cooldown is active for the current ability.
     */
    boolean isOnCooldown();
}
