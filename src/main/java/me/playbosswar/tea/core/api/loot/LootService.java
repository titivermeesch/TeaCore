package me.playbosswar.tea.core.api.loot;

import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Config-driven weighted loot tables, shared by every Tea game.
 *
 * <p>
 * A table is a config section the game owns:
 *
 * <pre>
 * loot:
 *   min-items: 10        # also accepted: min-items-per-chest
 *   max-items: 15        # also accepted: max-items-per-chest
 *   items:
 *     - { material: BOW, weight: 40, enchantments: ["POWER:1"] }
 *     - { material: GOLDEN_APPLE, weight: 20, amount: "1-3" }
 *     - { material: SPLASH_POTION, weight: 10, potion: HEALING }
 *     - { item: "fireball:explosive", weight: 20, amount: "1-2" }
 * </pre>
 *
 * Entry keys: {@code material} or {@code item} ({@code namespace:id} from a
 * registered {@link LootItemProvider}; {@code <namespace>: <id>} works too),
 * {@code weight} (default 1), {@code amount} (number or {@code "min-max"}),
 * {@code enchantments} ({@code "NAME:level"}, modern keys such as
 * {@code sharpness} or legacy names such as {@code DAMAGE_ALL}), {@code potion}
 * (a {@code PotionType}), {@code custom-name} and {@code lore} ({@code &} colour
 * codes). Invalid entries are skipped with a warning.
 */
public interface LootService {

    /**
     * Registers a provider for custom items under {@code namespace}.
     *
     * @param namespace lower-case namespace, e.g. {@code fireball}.
     * @param provider  builds items for ids in that namespace.
     */
    void registerItemProvider(String namespace, LootItemProvider provider);

    /**
     * Parses {@code section} into a table and registers it as {@code id},
     * replacing any table with that id (so it can be called again on reload).
     *
     * @param owner   plugin that owns the table, used for warnings.
     * @param id      table id, e.g. {@code fireball:chests}.
     * @param section the table's config section.
     * @return the registered table.
     */
    LootTable registerTable(Plugin owner, String id, ConfigurationSection section);

    /** Looks up a registered table. */
    Optional<LootTable> table(String id);
}
