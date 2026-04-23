package me.playbosswar.tea.core.api.kit;

import java.util.Map;
import me.playbosswar.tea.core.api.cooldown.CooldownDisplay;

/**
 * Wires a registered {@link Ability} into a {@link Kit} with a specific
 * trigger, cooldown,
 * and per-binding tuning.
 *
 * <p>
 * An ability is registered once via
 * {@link KitRegistry#registerAbility(me.playbosswar.tea.core.api.game.GameId, String, java.util.function.Supplier)}
 * and can be referenced from any number of kits through an
 * {@code AbilityBinding}.
 *
 * @param abilityId     id of the registered ability to invoke; never
 *                      {@code null}.
 * @param trigger       input event that fires the ability; never {@code null}.
 * @param cooldownTicks cooldown duration in ticks applied after each successful
 *                      trigger;
 *                      {@code 0} disables the cooldown.
 * @param display       how the cooldown surface is shown to the player; never
 *                      {@code null}.
 * @param config        per-binding configuration passed through to
 *                      {@link AbilityContext#config()}; defensively copied and
 *                      never
 *                      {@code null} after construction.
 */
public record AbilityBinding(
        String abilityId,
        AbilityTrigger trigger,
        int cooldownTicks,
        CooldownDisplay display,
        Map<String, Object> config) {

    /**
     * Canonical constructor that defensively copies {@code config} and normalizes
     * {@code null} to {@link Map#of()}.
     */
    public AbilityBinding {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
