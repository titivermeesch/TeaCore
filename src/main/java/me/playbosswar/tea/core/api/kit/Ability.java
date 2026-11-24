package me.playbosswar.tea.core.api.kit;

import java.util.function.Supplier;
import me.playbosswar.tea.core.api.game.GameId;

/**
 * Game-provided ability, registered with
 * {@link KitRegistry#registerAbility(GameId, String, Supplier)}. One instance is
 * created per player per binding, so per-player state can live on the instance.
 */
public interface Ability {

    /** Called on the main thread. */
    void onTrigger(AbilityContext ctx);
}
