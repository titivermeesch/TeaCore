package me.playbosswar.tea.core.api.kit;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.entity.Player;

/**
 * Central registry of ability factories, kits, and per-player kit selections.
 *
 * <p>
 * Games register their abilities and kits in {@code onEnable} and inspect
 * selections at
 * match start. Selections are kept per player in memory only.
 */
public interface KitRegistry {

    /**
     * Registers a factory that produces fresh {@link Ability} instances for an id.
     *
     * <p>
     * Games do this in {@code onEnable}. The registry calls the supplied factory
     * once per
     * player per binding so ability instances can hold per-player persistent state.
     *
     * @param gameId    owning game; must not be {@code null}.
     * @param abilityId id unique within the game; must not be {@code null}.
     * @param factory   factory producing new ability instances; must not be
     *                  {@code null}.
     */
    void registerAbility(GameId gameId, String abilityId, Supplier<Ability> factory);

    /**
     * Registers a kit.
     *
     * <p>
     * Overwrites any previous registration with the same
     * {@code (gameId, kitId)} pair.
     *
     * @param kit the kit to register; must not be {@code null}.
     */
    void registerKit(Kit kit);

    /**
     * Looks up a registered kit.
     *
     * @param gameId owning game; must not be {@code null}.
     * @param kitId  id of the kit; must not be {@code null}.
     * @return the kit, or empty if no kit is registered under that id.
     */
    Optional<Kit> kit(GameId gameId, String kitId);

    /**
     * Returns every kit registered for a game.
     *
     * @param gameId game whose kits to list; must not be {@code null}.
     * @return a snapshot collection, never {@code null}.
     */
    Collection<Kit> kitsFor(GameId gameId);

    /**
     * Stores a player's kit selection for the given game.
     *
     * <p>
     * The selection is read at match start by the arena to apply the kit's
     * abilities.
     * Overwrites any previous selection for the same {@code (player, game)} pair.
     *
     * @param player player making the selection; must not be {@code null}.
     * @param gameId game the selection applies to; must not be {@code null}.
     * @param kitId  id of the kit to select; must not be {@code null}.
     */
    void selectKit(Player player, GameId gameId, String kitId);

    /**
     * Returns the kit a player has selected for a game.
     *
     * @param player player to inspect; must not be {@code null}.
     * @param gameId game to check; must not be {@code null}.
     * @return the selected kit, or the first registered kit as fallback if none has
     *         been
     *         explicitly selected; empty only when no kits are registered for the
     *         game.
     */
    Optional<Kit> selectedKit(Player player, GameId gameId);

    /**
     * Checks whether a player has permanently unlocked a kit.
     *
     * <p>
     * Free kits ({@code kit.cost() == 0}) are always considered unlocked.
     * Priced kits are unlocked once the player has paid the cost via the kit
     * picker; the unlock record lives in the TeaCore database and persists
     * across restarts and across servers in the fleet.
     *
     * @param playerId player to check; must not be {@code null}.
     * @param kit      kit to check; must not be {@code null}.
     * @return {@code true} when the kit is free or the player owns the unlock.
     */
    boolean isUnlocked(UUID playerId, Kit kit);

    /**
     * Records a permanent unlock for a {@code (player, game, kit)} tuple.
     *
     * <p>
     * Idempotent: calling this for an already-unlocked kit is a no-op and
     * returns {@code true}. This method does not deduct the kit cost from the
     * player's balance — callers are expected to withdraw the cost through
     * {@link me.playbosswar.tea.core.api.economy.EconomyService} before
     * recording the unlock.
     *
     * @param playerId player to grant the unlock to; must not be {@code null}.
     * @param kit      kit to unlock; must not be {@code null}.
     * @return {@code true} if the row now exists (either just inserted or
     *         already present); {@code false} on database failure.
     */
    boolean unlock(UUID playerId, Kit kit);

    /**
     * Places the kit's starter items into the player's inventory at their declared
     * slot
     * indices.
     *
     * <p>
     * Does not clear the player's existing inventory first; callers that want a
     * clean
     * slate should clear before calling. Slot indices follow Bukkit's
     * {@code PlayerInventory}
     * layout ({@code 0-8} hotbar, {@code 9-35} main, {@code 36-39} armor,
     * {@code 40} off-hand).
     * Items are cloned before being placed so later mutations on the kit's stored
     * stacks do
     * not bleed into player inventories. A no-op when the kit declares no items.
     *
     * @param player player to equip; must not be {@code null}.
     * @param kit    kit whose items to apply; must not be {@code null}.
     */
    void applyItemsTo(Player player, Kit kit);
}
