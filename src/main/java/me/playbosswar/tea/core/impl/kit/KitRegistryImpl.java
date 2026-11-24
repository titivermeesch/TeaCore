package me.playbosswar.tea.core.impl.kit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.kit.Ability;
import me.playbosswar.tea.core.api.kit.Kit;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class KitRegistryImpl implements KitRegistry {

    private final ConcurrentHashMap<GameId, ConcurrentHashMap<String, Supplier<Ability>>> abilityFactories = new ConcurrentHashMap<>();
    // LinkedHashMap keeps registration order for the kit picker. Not thread-safe,
    // so it is guarded by the kits mutex.
    private final java.util.concurrent.ConcurrentHashMap<GameId, java.util.LinkedHashMap<String, Kit>> kits = new java.util.concurrent.ConcurrentHashMap<>();
    private final Object kitsMutex = new Object();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<GameId, String>> selections = new ConcurrentHashMap<>();

    private final DataSource dataSource;
    private final Logger logger;

    public KitRegistryImpl(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    @Override
    public void registerAbility(GameId gameId, String abilityId, Supplier<Ability> factory) {
        abilityFactories.computeIfAbsent(gameId, g -> new ConcurrentHashMap<>())
                .put(abilityId, factory);
    }

    @Override
    public void registerKit(Kit kit) {
        synchronized (kitsMutex) {
            kits.computeIfAbsent(kit.gameId(), g -> new java.util.LinkedHashMap<>())
                    .put(kit.kitId(), kit);
        }
    }

    @Override
    public void unregisterKit(GameId gameId, String kitId) {
        synchronized (kitsMutex) {
            java.util.LinkedHashMap<String, Kit> forGame = kits.get(gameId);
            if (forGame != null) {
                forGame.remove(kitId);
            }
        }
    }

    @Override
    public Optional<Kit> kit(GameId gameId, String kitId) {
        synchronized (kitsMutex) {
            java.util.LinkedHashMap<String, Kit> forGame = kits.get(gameId);
            return forGame == null ? Optional.empty() : Optional.ofNullable(forGame.get(kitId));
        }
    }

    @Override
    public Collection<Kit> kitsFor(GameId gameId) {
        synchronized (kitsMutex) {
            java.util.LinkedHashMap<String, Kit> forGame = kits.get(gameId);
            return forGame == null ? Collections.emptyList() : List.copyOf(forGame.values());
        }
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
    public boolean isUnlocked(UUID playerId, Kit kit) {
        if (kit.cost() <= 0) return true;
        if (dataSource == null || playerId == null) return false;
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM tea_kit_unlocks WHERE player_uuid = ? AND game_id = ? AND kit_id = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, kit.gameId().value());
            ps.setString(3, kit.kitId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Kit-unlock lookup failed for " + playerId + " / " + kit.kitId(), e);
            return false;
        }
    }

    @Override
    public boolean unlock(UUID playerId, Kit kit) {
        if (dataSource == null || playerId == null) return false;
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tea_kit_unlocks (player_uuid, game_id, kit_id, unlocked_at) "
                                + "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, kit.gameId().value());
            ps.setString(3, kit.kitId());
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            // Likely a PK collision (already unlocked): treat as success.
            if (isUnlocked(playerId, kit)) return true;
            logger.log(Level.WARNING, "Failed to record kit unlock for " + playerId + " / " + kit.kitId(), e);
            return false;
        }
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
