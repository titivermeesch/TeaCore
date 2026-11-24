package me.playbosswar.tea.core.api.kit;

import java.util.Map;
import me.playbosswar.tea.core.api.cooldown.CooldownDisplay;

/**
 * Wires an ability registered via
 * {@link KitRegistry#registerAbility(me.playbosswar.tea.core.api.game.GameId, String, java.util.function.Supplier)}
 * into a {@link Kit}. One ability can be bound in any number of kits.
 *
 * @param cooldownTicks applied after each successful trigger; {@code 0} disables it.
 * @param config        passed through to {@link AbilityContext#config()}.
 */
public record AbilityBinding(
        String abilityId,
        AbilityTrigger trigger,
        int cooldownTicks,
        CooldownDisplay display,
        Map<String, Object> config) {

    public AbilityBinding {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
