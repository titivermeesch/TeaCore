package me.playbosswar.tea.core.impl.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.loot.LootItemProvider;
import me.playbosswar.tea.core.api.loot.LootService;
import me.playbosswar.tea.core.api.loot.LootTable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

/** Default {@link LootService}. */
public final class LootServiceImpl implements LootService {

    /** Pre-1.20.5 Bukkit enchantment names still found in older configs. */
    private static final Map<String, String> LEGACY_ENCHANTS = Map.ofEntries(
            Map.entry("DAMAGE_ALL", "sharpness"), Map.entry("DAMAGE_UNDEAD", "smite"),
            Map.entry("DAMAGE_ARTHROPODS", "bane_of_arthropods"), Map.entry("DIG_SPEED", "efficiency"),
            Map.entry("DURABILITY", "unbreaking"), Map.entry("LOOT_BONUS_BLOCKS", "fortune"),
            Map.entry("LOOT_BONUS_MOBS", "looting"), Map.entry("PROTECTION_ENVIRONMENTAL", "protection"),
            Map.entry("PROTECTION_FIRE", "fire_protection"), Map.entry("PROTECTION_FALL", "feather_falling"),
            Map.entry("PROTECTION_EXPLOSIONS", "blast_protection"),
            Map.entry("PROTECTION_PROJECTILE", "projectile_protection"), Map.entry("OXYGEN", "respiration"),
            Map.entry("WATER_WORKER", "aqua_affinity"), Map.entry("ARROW_DAMAGE", "power"),
            Map.entry("ARROW_KNOCKBACK", "punch"), Map.entry("ARROW_FIRE", "flame"),
            Map.entry("ARROW_INFINITE", "infinity"), Map.entry("LUCK", "luck_of_the_sea"));

    private static final Set<String> RESERVED_KEYS = Set.of("material", "item", "weight", "amount",
            "enchantments", "potion", "custom-name", "lore", "slot");

    private final Logger logger;
    private final Map<String, LootItemProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, LootTable> tables = new ConcurrentHashMap<>();

    public LootServiceImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void registerItemProvider(String namespace, LootItemProvider provider) {
        providers.put(namespace.toLowerCase(Locale.ROOT), provider);
    }

    @Override
    public LootTable registerTable(Plugin owner, String id, ConfigurationSection section) {
        int min = section.getInt("min-items", section.getInt("min-items-per-chest", 10));
        int max = section.getInt("max-items", section.getInt("max-items-per-chest", 15));
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (Object raw : section.getList("items", List.of())) {
            index++;
            Entry entry = parse(raw);
            if (entry == null) {
                owner.getLogger().warning("Loot table '" + id + "': skipping invalid entry #" + index + ": " + raw);
            } else if (entry.weight() > 0) {
                entries.add(entry);
            }
        }
        LootTable table = new Table(id, Math.max(0, min), Math.max(0, max), List.copyOf(entries));
        tables.put(id, table);
        return table;
    }

    @Override
    public Optional<LootTable> table(String id) {
        return Optional.ofNullable(tables.get(id));
    }

    // --- Parsing ---

    /** One weighted entry; the item is built fresh on every roll. */
    private record Entry(Map<String, Object> spec, int weight) { }

    @SuppressWarnings("unchecked")
    private Entry parse(Object raw) {
        Map<String, Object> spec;
        if (raw instanceof String s) {
            spec = Map.of("material", s);
        } else if (raw instanceof Map<?, ?> map) {
            spec = (Map<String, Object>) map;
        } else {
            return null;
        }
        int weight = spec.get("weight") instanceof Number n ? n.intValue() : 1;
        if (spec.containsKey("material")) {
            if (Material.matchMaterial(String.valueOf(spec.get("material")).trim()) == null) {
                return null;
            }
        } else if (itemRef(spec) == null) {
            return null;
        }
        return new Entry(spec, weight);
    }

    /** {@code item: "ns:id"} or the shorthand {@code ns: id}; null if neither. */
    private static String itemRef(Map<String, Object> spec) {
        Object item = spec.get("item");
        if (item instanceof String s && s.contains(":")) {
            return s.trim();
        }
        for (Map.Entry<String, Object> e : spec.entrySet()) {
            if (!RESERVED_KEYS.contains(e.getKey()) && e.getValue() instanceof String v) {
                return e.getKey().trim() + ":" + v.trim();
            }
        }
        return null;
    }

    private Optional<ItemStack> build(Map<String, Object> spec, Random random) {
        int amount = parseAmount(spec.get("amount"), random);
        ItemStack item;
        if (spec.containsKey("material")) {
            Material material = Material.matchMaterial(String.valueOf(spec.get("material")).trim());
            if (material == null) {
                return Optional.empty();
            }
            item = new ItemStack(material, amount);
        } else {
            String ref = itemRef(spec);
            int colon = ref == null ? -1 : ref.indexOf(':');
            if (colon <= 0) {
                return Optional.empty();
            }
            LootItemProvider provider = providers.get(ref.substring(0, colon).toLowerCase(Locale.ROOT));
            if (provider == null) {
                logger.warning("Loot item '" + ref + "' has no registered provider; skipping.");
                return Optional.empty();
            }
            Optional<ItemStack> provided = provider.create(ref.substring(colon + 1), amount);
            if (provided.isEmpty()) {
                return Optional.empty();
            }
            item = provided.get();
        }
        applyMeta(item, spec);
        return Optional.of(item);
    }

    private static void applyMeta(ItemStack item, Map<String, Object> spec) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (spec.get("custom-name") instanceof String name) {
            meta.setDisplayName(name.replace('&', '§'));
        }
        if (spec.get("lore") instanceof List<?> lines) {
            meta.setLore(lines.stream().map(String::valueOf).map(l -> l.replace('&', '§')).toList());
        }
        if (spec.get("potion") instanceof String potion && meta instanceof PotionMeta potionMeta) {
            try {
                potionMeta.setBasePotionType(PotionType.valueOf(potion.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown potion type: leave it plain
            }
        }
        if (spec.get("enchantments") instanceof List<?> list) {
            for (Object entry : list) {
                String[] parts = String.valueOf(entry).split(":");
                Enchantment enchantment = enchantment(parts[0]);
                if (enchantment != null) {
                    int level = parts.length > 1 ? parseInt(parts[1].trim(), 1) : 1;
                    meta.addEnchant(enchantment, level, true);
                }
            }
        }
        item.setItemMeta(meta);
    }

    @SuppressWarnings("deprecation")
    static Enchantment enchantment(String name) {
        String trimmed = name.trim();
        String key = LEGACY_ENCHANTS.getOrDefault(trimmed.toUpperCase(Locale.ROOT), trimmed.toLowerCase(Locale.ROOT));
        Enchantment found = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        return found != null ? found : Enchantment.getByName(trimmed.toUpperCase(Locale.ROOT));
    }

    private static int parseAmount(Object value, Random random) {
        if (value instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        if (value instanceof String s && s.contains("-")) {
            String[] parts = s.split("-");
            int min = Math.max(1, parseInt(parts[0].trim(), 1));
            int max = parseInt(parts[1].trim(), min);
            return min >= max ? min : min + random.nextInt(max - min + 1);
        }
        return value == null ? 1 : Math.max(1, parseInt(String.valueOf(value), 1));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // --- Table ---

    private final class Table implements LootTable {
        private final String id;
        private final int min;
        private final int max;
        private final List<Entry> entries;
        private final int totalWeight;

        Table(String id, int min, int max, List<Entry> entries) {
            this.id = id;
            this.min = min;
            this.max = max;
            this.entries = entries;
            this.totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<ItemStack> roll() {
            Random random = ThreadLocalRandom.current();
            List<ItemStack> out = new ArrayList<>();
            if (totalWeight <= 0) {
                return out;
            }
            int count = min >= max ? min : min + random.nextInt(max - min + 1);
            for (int i = 0; i < count; i++) {
                int target = random.nextInt(totalWeight);
                int cumulative = 0;
                for (Entry entry : entries) {
                    cumulative += entry.weight();
                    if (target < cumulative) {
                        build(entry.spec(), random).ifPresent(out::add);
                        break;
                    }
                }
            }
            return out;
        }

        @Override
        public void fill(Inventory inventory) {
            inventory.clear();
            Random random = ThreadLocalRandom.current();
            for (ItemStack item : roll()) {
                boolean placed = false;
                for (int attempt = 0; attempt < 30 && !placed; attempt++) {
                    int slot = random.nextInt(inventory.getSize());
                    if (inventory.getItem(slot) == null) {
                        inventory.setItem(slot, item);
                        placed = true;
                    }
                }
                if (!placed) {
                    inventory.addItem(item);
                }
            }
        }
    }
}
