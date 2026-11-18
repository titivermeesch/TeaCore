package me.playbosswar.tea.core.impl.placeholder;

import java.util.Locale;
import java.util.Map;
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
    private final me.playbosswar.tea.core.impl.sidebar.ArenaValues arenaValues;
    private final LeaderboardCache leaderboards;

    public CorePlaceholders(TeaCoreAPI api, CoreStatsService coreStats,
                            me.playbosswar.tea.core.impl.sidebar.ArenaValues arenaValues,
                            LeaderboardCache leaderboards) {
        this.api = api;
        this.coreStats = coreStats;
        this.arenaValues = arenaValues;
        this.leaderboards = leaderboards;
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
            case "arenas_total" -> String.valueOf(api.coordination().allArenas().size());
            case "players_in_games" -> String.valueOf(api.coordination().allArenas().stream()
                    .mapToInt(a -> a.players()).sum());
            default -> {
                if (key.startsWith("top_")) {
                    yield leaderboard(key.substring(4));
                }
                if (key.startsWith("game_")) {
                    String game = game(key.substring(5));
                    if (game != null) {
                        yield game;
                    }
                }
                yield player == null ? null : resolvePlayer(player, key);
            }
        };
    }

    /**
     * {@code top_<board>_<n>_name|value}, where board is {@code core_<stat>} or
     * {@code <gameId>_<stat>} (stat = slug of the stats column label).
     */
    @Nullable
    private String leaderboard(String rest) {
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return null;
        }
        String field = rest.substring(lastUnderscore + 1);
        String withoutField = rest.substring(0, lastUnderscore);
        int posUnderscore = withoutField.lastIndexOf('_');
        if (posUnderscore <= 0 || !(field.equals("name") || field.equals("value"))) {
            return null;
        }
        int position;
        try {
            position = Integer.parseInt(withoutField.substring(posUnderscore + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        LeaderboardCache.Entry entry = leaderboards.entry(withoutField.substring(0, posUnderscore), position);
        if (entry == null) {
            return field.equals("name") ? "-" : "0";
        }
        return field.equals("name") ? entry.name() : entry.value();
    }

    /**
     * Fleet-wide game counters: {@code game_<id>_players}, {@code _arenas},
     * {@code _arenas_<state>}, {@code _queue}, {@code _maps}, {@code _name}.
     */
    @Nullable
    private String game(String rest) {
        for (var def : api.games().all()) {
            String id = def.id().value();
            if (!rest.startsWith(id + "_")) {
                continue;
            }
            String what = rest.substring(id.length() + 1);
            var arenas = api.coordination().allArenas().stream().filter(a -> a.gameId().equals(def.id())).toList();
            if (what.equals("players")) {
                return String.valueOf(arenas.stream().mapToInt(a -> a.players()).sum());
            }
            if (what.equals("arenas")) {
                return String.valueOf(arenas.size());
            }
            if (what.startsWith("arenas_")) {
                String state = what.substring(7).toUpperCase(Locale.ROOT);
                return String.valueOf(arenas.stream().filter(a -> a.state().name().equals(state)).count());
            }
            if (what.equals("queue")) {
                return String.valueOf(api.coordination().queueFor(def.id()).size());
            }
            if (what.equals("maps")) {
                return String.valueOf(api.maps().forGame(def.id()).size());
            }
            if (what.equals("name")) {
                return arenaValues.gameName(api.messageService().defaultLocale(), def.id());
            }
        }
        return null;
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
            case "assists" -> String.valueOf(coreStats.statsOf(uuid).assists());
            case "winrate" -> winRate(uuid);
            case "kdr" -> killDeathRatio(uuid);
            case "in_arena" -> String.valueOf(api.arenas().arenaOf(uuid).isPresent());
            case "arena_id", "arena_game", "arena_map", "arena_state",
                    "arena_players", "arena_max_players", "arena_time_left" -> arena(uuid, key);
            case "team", "team_id" -> team(uuid, key);
            case "queue_game", "queue_position", "queue_size" -> queue(uuid, key);
            case "match_kills", "match_deaths", "match_assists" -> current(player, key.substring(6));
            case "kit" -> current(player, "kit");
            case "achievements_unlocked" -> String.valueOf(api.games().all().stream()
                    .mapToLong(def -> unlocked(uuid, def.id())).sum());
            default -> {
                if (key.startsWith("arena_")) {
                    // Every sidebar value, e.g. arena_time (m:ss), arena_phase,
                    // arena_waiting_for, arena_map_name, arena_winner.
                    String tag = key.substring(6).replace('_', '-');
                    yield current(player, tag.equals("map-name") ? "map" : tag.equals("game-name") ? "game-name" : tag);
                }
                if (key.startsWith("kit_")) {
                    yield kitFor(player, key.substring(4));
                }
                if (key.startsWith("kits_unlocked_")) {
                    yield kitsUnlocked(uuid, key.substring(14));
                }
                if (key.startsWith("achievements_unlocked_")) {
                    yield gameId(key.substring(22)).map(g -> String.valueOf(unlocked(uuid, g))).orElse(null);
                }
                if (key.startsWith("achievements_total_")) {
                    yield gameId(key.substring(19))
                            .map(g -> String.valueOf(api.achievements().forGame(g).size())).orElse(null);
                }
                yield null;
            }
        };
    }

    /** A value from the player's current arena (same values as sidebar tags), or "". */
    private String current(OfflinePlayer player, String tag) {
        Player online = player.getPlayer();
        Arena arena = online == null ? null : api.arenas().arenaOf(online.getUniqueId()).orElse(null);
        if (arena == null) {
            return "";
        }
        return arenaValues.forViewer(online, arena).getOrDefault(tag, "");
    }

    private java.util.Optional<me.playbosswar.tea.core.api.game.GameId> gameId(String id) {
        return api.games().all().stream().map(def -> def.id()).filter(g -> g.value().equals(id)).findFirst();
    }

    private long unlocked(UUID uuid, me.playbosswar.tea.core.api.game.GameId game) {
        return api.achievements().forGame(game).stream()
                .filter(a -> api.achievements().isUnlocked(uuid, game, a.id())).count();
    }

    @Nullable
    private String kitFor(OfflinePlayer player, String game) {
        Player online = player.getPlayer();
        if (online == null) {
            return null;
        }
        return gameId(game).flatMap(g -> api.kits().selectedKit(online, g))
                .map(k -> api.messageService().renderPlainText(
                        api.messageService().resolveLocale(online), k.displayNameKey(), Map.of()))
                .orElse("-");
    }

    @Nullable
    private String kitsUnlocked(UUID uuid, String game) {
        return gameId(game).map(g -> String.valueOf(api.kits().kitsFor(g).stream()
                .filter(k -> k.cost() <= 0 || api.kits().isUnlocked(uuid, k)).count())).orElse(null);
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
        Player online = Bukkit.getPlayer(uuid);
        String locale = online == null ? api.messageService().defaultLocale()
                : api.messageService().resolveLocale(online);
        return api.teams().teamOf(arena.id(), uuid)
                .map(t -> key.equals("team_id") ? t.id().value()
                        : api.messageService().renderLegacy(locale, t.displayNameKey(), Map.of()))
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
