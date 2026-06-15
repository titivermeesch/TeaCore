package me.playbosswar.tea.core.impl.placeholder;

import java.util.Locale;
import java.util.UUID;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.placeholder.PlaceholderResolver;
import me.playbosswar.tea.core.impl.stats.CoreStatsService;
import me.playbosswar.tea.core.impl.stats.CoreStatsService.CoreStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * TeaCore's built-in placeholders: player identity, economy, core stats
 * (wins/deaths/etc. plus derived win-rate and KDR), the player's current arena
 * and queue, their team, and a few fleet-level counts. Reached as
 * {@code %tea_<key>%} when no game namespace claims the key.
 */
public final class CorePlaceholders implements PlaceholderResolver {

    private final TeaCoreAPI api;
    private final CoreStatsService coreStats;

    public CorePlaceholders(TeaCoreAPI api, CoreStatsService coreStats) {
        this.api = api;
        this.coreStats = coreStats;
    }

    @Override
    @Nullable
    public String resolve(@Nullable OfflinePlayer player, String key) {
        return switch (key) {
            case "version" -> api.version();
            case "server", "server_id" -> api.serverId();
            case "standalone" -> String.valueOf(api.isStandalone());
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "game_count" -> String.valueOf(api.games().all().size());
            case "map_count" -> String.valueOf(api.maps().all().size());
            default -> player == null ? null : resolvePlayer(player, key);
        };
    }

    @Nullable
    private String resolvePlayer(OfflinePlayer player, String key) {
        UUID uuid = player.getUniqueId();
        return switch (key) {
            case "name" -> player.getName();
            case "uuid" -> uuid.toString();
            case "displayname" -> displayName(player);
            case "balance" -> balance(uuid);
            case "balance_formatted" -> balanceFormatted(uuid);
            case "wins" -> String.valueOf(coreStats.statsOf(uuid).wins());
            case "losses" -> String.valueOf(coreStats.statsOf(uuid).losses());
            case "games_played" -> String.valueOf(coreStats.statsOf(uuid).gamesPlayed());
            case "kills" -> String.valueOf(coreStats.statsOf(uuid).kills());
            case "deaths" -> String.valueOf(coreStats.statsOf(uuid).deaths());
            case "winrate" -> winRate(uuid);
            case "kdr" -> killDeathRatio(uuid);
            case "in_arena" -> String.valueOf(api.arenas().arenaOf(uuid).isPresent());
            case "arena_id", "arena_game", "arena_map", "arena_state",
                    "arena_players", "arena_max_players", "arena_time_left" -> arena(uuid, key);
            case "team", "team_id" -> team(uuid, key);
            case "queue_game", "queue_position", "queue_size" -> queue(uuid, key);
            default -> null;
        };
    }

    private String displayName(OfflinePlayer player) {
        Player online = player.getPlayer();
        return online != null ? online.getDisplayName() : String.valueOf(player.getName());
    }

    @Nullable
    private String balance(UUID uuid) {
        EconomyService economy = api.economy();
        if (economy == null || !economy.isAvailable()) {
            return null;
        }
        return String.format(Locale.ROOT, "%.2f", economy.balance(uuid));
    }

    @Nullable
    private String balanceFormatted(UUID uuid) {
        EconomyService economy = api.economy();
        if (economy == null || !economy.isAvailable()) {
            return null;
        }
        return economy.format(economy.balance(uuid));
    }

    private String winRate(UUID uuid) {
        CoreStats stats = coreStats.statsOf(uuid);
        if (stats.gamesPlayed() == 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", 100.0 * stats.wins() / stats.gamesPlayed());
    }

    private String killDeathRatio(UUID uuid) {
        CoreStats stats = coreStats.statsOf(uuid);
        int deaths = Math.max(1, stats.deaths());
        return String.format(Locale.ROOT, "%.2f", (double) stats.kills() / deaths);
    }

    private String arena(UUID uuid, String key) {
        Arena arena = api.arenas().arenaOf(uuid).orElse(null);
        if (arena == null) {
            return "";
        }
        return switch (key) {
            case "arena_id" -> arena.id().value().toString().substring(0, 8);
            case "arena_game" -> arena.gameId().value();
            case "arena_map" -> arena.mapId().orElse("-");
            case "arena_state" -> arena.state().name();
            case "arena_players" -> String.valueOf(arena.players().size());
            case "arena_max_players" -> String.valueOf(arena.maxPlayers());
            case "arena_time_left" -> String.valueOf(arena.phaseSecondsRemaining());
            default -> "";
        };
    }

    private String team(UUID uuid, String key) {
        Arena arena = api.arenas().arenaOf(uuid).orElse(null);
        if (arena == null) {
            return "";
        }
        return api.teams().teamOf(arena.id(), uuid)
                .map(t -> key.equals("team_id") ? t.id().value() : t.displayNameKey())
                .orElse("");
    }

    private String queue(UUID uuid, String key) {
        QueueEntry entry = api.coordination().queueEntryOf(uuid).orElse(null);
        if (entry == null) {
            return "";
        }
        return switch (key) {
            case "queue_game" -> entry.gameId().value();
            case "queue_position" -> String.valueOf(api.coordination().queuePosition(uuid, entry.gameId()) + 1);
            case "queue_size" -> String.valueOf(api.coordination().queueFor(entry.gameId()).size());
            default -> "";
        };
    }
}
