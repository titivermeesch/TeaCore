package me.playbosswar.tea.core.impl.kit;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.kit.Ability;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class KitRegistryImpl implements KitRegistry {

    private final ConcurrentHashMap<GameId, ConcurrentHashMap<String, Supplier<Ability>>> abilityFactories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GameId, ConcurrentHashMap<String, Kit>> kits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<GameId, String>> selections = new ConcurrentHashMap<>();

    @Override
    public void registerAbility(GameId gameId, String abilityId, Supplier<Ability> factory) {
        abilityFactories.computeIfAbsent(gameId, g -> new ConcurrentHashMap<>())
                .put(abilityId, factory);
    }

    @Override
    public void registerKit(Kit kit) {
        kits.computeIfAbsent(kit.gameId(), g -> new ConcurrentHashMap<>())
                .put(kit.kitId(), kit);
    }

    @Override
    public Optional<Kit> kit(GameId gameId, String kitId) {
        ConcurrentHashMap<String, Kit> forGame = kits.get(gameId);
        return forGame == null ? Optional.empty() : Optional.ofNullable(forGame.get(kitId));
    }

    @Override
    public Collection<Kit> kitsFor(GameId gameId) {
        ConcurrentHashMap<String, Kit> forGame = kits.get(gameId);
        return forGame == null ? Collections.emptyList() : Collections.unmodifiableCollection(forGame.values());
    }

    @Override
    public void selectKit(Player player, GameId gameId, String kitId) {
        selections.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>())
                .put(gameId, kitId);
    }

    @Override
    public Optional<Kit> selectedKit(Player player, GameId gameId) {
        ConcurrentHashMap<GameId, String> sel = selections.get(player.getUniqueId());
        String kitId = sel == null ? null : sel.get(gameId);
        if (kitId != null) {
            return kit(gameId, kitId);
        }
        return kitsFor(gameId).stream().findFirst();
    }

    @Override
    public void applyItemsTo(Player player, Kit kit) {
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<Integer, ItemStack> entry : kit.items().entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack == null)
                continue;
            inv.setItem(entry.getKey(), stack.clone());
        }
    }

    public Supplier<Ability> abilityFactory(GameId gameId, String abilityId) {
        ConcurrentHashMap<String, Supplier<Ability>> forGame = abilityFactories.get(gameId);
        return forGame == null ? null : forGame.get(abilityId);
    }
}
