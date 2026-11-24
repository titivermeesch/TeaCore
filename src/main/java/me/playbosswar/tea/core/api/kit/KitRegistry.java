package me.playbosswar.tea.core.api.kit;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.entity.Player;

/**
 * Registry of ability factories, kits and per-player kit selections. Register
 * in {@code onEnable}. Selections are held in memory only; unlocks are
 * persisted.
 */
public interface KitRegistry {

    /**
     * The factory is called once per player per binding, so an {@link Ability}
     * instance can hold per-player state.
     *
     * @param abilityId id unique within the game
     */
    void registerAbility(GameId gameId, String abilityId, Supplier<Ability> factory);

    /** Replaces any kit already registered with the same {@code (gameId, kitId)}. */
    void registerKit(Kit kit);

    /** Removes a kit, for example after it was deleted from the config. Players who picked it get the default kit. */
    void unregisterKit(GameId gameId, String kitId);

    Optional<Kit> kit(GameId gameId, String kitId);

    /** @return a snapshot */
    Collection<Kit> kitsFor(GameId gameId);

    /** The arena reads the selection at match start to apply the kit's abilities. */
    void selectKit(Player player, GameId gameId, String kitId);

    /**
     * @return the selected kit, else the first registered kit; empty only when
     *         the game has no kits
     */
    Optional<Kit> selectedKit(Player player, GameId gameId);

    /**
     * Free kits are always unlocked. Unlocks are stored in the TeaCore database
     * and shared across servers.
     */
    boolean isUnlocked(UUID playerId, Kit kit);

    /**
     * Records a permanent unlock. Idempotent. Does not charge the player;
     * withdraw the cost through
     * {@link me.playbosswar.tea.core.api.economy.EconomyService} first.
     *
     * @return {@code true} if the kit is now unlocked, {@code false} on
     *         database failure
     */
    boolean unlock(UUID playerId, Kit kit);

    /**
     * Places clones of the kit's starter items in their slots. Does not clear
     * the inventory first.
     */
    void applyItemsTo(Player player, Kit kit);
}
