package me.playbosswar.tea.core.impl.placeholder;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.playbosswar.tea.core.api.placeholder.PlaceholderResolver;
import me.playbosswar.tea.core.api.stats.StatsRegistry;
import me.playbosswar.tea.core.api.stats.StatsSchema;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Auto-exposes every registered game's stats as {@code %tea_<gameId>_<stat>%},
 * with the stat key being the slugified column label (e.g. "Games won" becomes
 * {@code games_won}). Consulted as a fallback after a game's own custom
 * resolvers, so games can override or add to their stat placeholders. Schema and
 * per-player lookups are cached to keep PlaceholderAPI's frequent calls off the
 * database hot path.
 */
public final class GameStatsPlaceholders implements PlaceholderResolver {

    private final StatsRegistry stats;
    private final Cache<Boolean, List<StatsSchema>> schemaCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();
    private final Cache<String, Map<String, String>> valueCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(2_000)
            .build();

    public GameStatsPlaceholders(StatsRegistry stats) {
        this.stats = stats;
    }

    @Override
    @Nullable
    public String resolve(@Nullable OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        for (StatsSchema schema : schemas()) {
            String gameId = schema.gameId().value();
            if (!params.startsWith(gameId + "_")) {
                continue;
            }
            String slug = params.substring(gameId.length() + 1);
            String value = values(schema, uuid).get(slug);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<StatsSchema> schemas() {
        return schemaCache.get(Boolean.TRUE, k -> List.copyOf(stats.allSchemas()));
    }

    private Map<String, String> values(StatsSchema schema, UUID uuid) {
        return valueCache.get(schema.gameId().value() + "|" + uuid, k -> {
            Map<String, String> bySlug = new LinkedHashMap<>();
            stats.providerFor(schema).fetch(uuid).forEach((label, value) -> bySlug.put(slug(label), value));
            return bySlug;
        });
    }

    private static String slug(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
