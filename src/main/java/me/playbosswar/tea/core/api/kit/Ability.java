package me.playbosswar.tea.core.api.kit;

import me.playbosswar.tea.core.api.game.GameId;
import java.util.function.Supplier;

/**
 * A game-provided ability that fires when a kit binding's trigger matches.
 *
 * <p>Implement as a normal Java class with a no-arg constructor and register via
 * {@link KitRegistry#registerAbility(GameId, String, Supplier)}. The registry creates one
 * instance per player per binding using the supplied factory, so per-player persistent
 * state may live on the ability instance, while per-invocation state should be kept on the
 * {@link AbilityContext}.
 */
public interface Ability {

    /**
     * Called when the ability's trigger fires for a player.
     *
     * <p>Side effects are fully up to the implementation: firing events, changing the
     * player's inventory, spawning particles, etc. Called on the main thread.
     *
     * @param ctx runtime context for this invocation; never {@code null}.
     */
    void onTrigger(AbilityContext ctx);
}
