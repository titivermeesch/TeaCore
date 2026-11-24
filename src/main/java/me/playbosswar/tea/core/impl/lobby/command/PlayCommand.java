package me.playbosswar.tea.core.impl.lobby.command;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.QueueEntry;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PlayCommand implements TabExecutor {

    private final Plugin plugin;
    private final TeaCoreAPI core;

    public PlayCommand(Plugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messageService().sendToSender(sender, "core.command.player-only");
            return true;
        }
        if (args.length == 0) {
            if (!core.menus().open(player, "play", java.util.Map.of())) {
                core.messageService().send(player, "lobby.play.usage");
            }
            return true;
        }

        GameId gameId = GameId.of(args[0].toLowerCase(Locale.ROOT));
        String mapFilter = args.length >= 2 ? args[1] : null;
        boolean ranked = "ranked".equalsIgnoreCase(mapFilter);
        if (ranked) {
            mapFilter = null;
            var ratings = ratings();
            if (ratings == null || !ratings.enabled(gameId)) {
                core.messageService().send(player, "core.ranked.off", Map.of("game", gameId.value()));
                return true;
            }
            if (!player.hasPermission("tea.ranked")) {
                core.messageService().send(player, "core.command.no-permission");
                return true;
            }
        }

        // A party leader's /play brings every member on this server who is not in a match.
        List<Player> group = new java.util.ArrayList<>(List.of(player));
        String partyId = null;
        var parties = plugin instanceof me.playbosswar.tea.core.impl.TeaCorePlugin tc ? tc.parties() : null;
        var party = parties == null ? Optional.<me.playbosswar.tea.core.impl.party.PartyProvider.Party>empty()
                : parties.partyOf(player.getUniqueId());
        if (party.isPresent()) {
            if (!party.get().leader().equals(player.getUniqueId()) && parties.leaderOnly()) {
                core.messageService().send(player, "core.party.leader-only");
                return true;
            }
            partyId = party.get().id();
            for (java.util.UUID uuid : party.get().members()) {
                Player member = org.bukkit.Bukkit.getPlayer(uuid);
                if (member != null && !member.equals(player) && core.arenas().arenaOf(uuid).isEmpty()) {
                    group.add(member);
                }
            }
        }

        if (core.isStandalone()) {
            handleStandalone(group, gameId, mapFilter, ranked);
        } else {
            handleQueued(group, gameId, mapFilter, ranked, partyId);
        }
        return true;
    }

    private int groupRating(List<Player> group, GameId gameId) {
        var ratings = ratings();
        if (ratings == null) {
            return 0;
        }
        return (int) group.stream().mapToInt(p -> ratings.get(p.getUniqueId(), gameId).rating()).average().orElse(0);
    }

    private void tellPulled(List<Player> group, GameId gameId) {
        for (Player member : group.subList(1, group.size())) {
            core.messageService().send(member, "core.party.pulled", Map.of(
                    "leader", group.get(0).getName(), "game", gameId.value()));
        }
    }

    private me.playbosswar.tea.core.impl.rating.RatingService ratings() {
        return plugin instanceof me.playbosswar.tea.core.impl.TeaCorePlugin tc ? tc.ratings() : null;
    }

    private void handleStandalone(List<Player> group, GameId gameId, String mapFilter, boolean ranked) {
        Player player = group.get(0);
        Optional<GameDefinition> optDef = core.games().get(gameId);
        if (optDef.isEmpty()) {
            core.messageService().send(player, "lobby.play.unknown-game",
                    Map.of("game", gameId.value()));
            return;
        }

        var ratings = ratings();
        int rating = groupRating(group, gameId);
        Arena target = findWaitingArena(gameId, mapFilter, ranked, rating, group.size())
                .orElseGet(() -> allocate(player, gameId, mapFilter));
        if (target == null)
            return;

        if (ratings != null) {
            ratings.intend(player.getUniqueId(), ranked);
        }
        boolean joined = core.arenas().addPlayer(target.id(), player, false);
        if (joined && group.size() > 1) {
            for (Player member : group.subList(1, group.size())) {
                if (ratings != null) {
                    ratings.intend(member.getUniqueId(), ranked);
                }
                core.arenas().addPlayer(target.id(), member, false);
            }
            tellPulled(group, gameId);
        }
        if (joined && mapFilter != null && plugin instanceof me.playbosswar.tea.core.impl.TeaCorePlugin tc
                && tc.mapVoting() != null) {
            // The player asked for this map: don't vote it away.
            tc.mapVoting().lock(target.id());
        }
        if (joined) {
            core.messageService().send(player, "lobby.play.joined", Map.of(
                    "game", gameId.value(),
                    "arena", target.id().value().toString().substring(0, 8),
                    "map", target.mapId().orElse("-")));
        } else {
            core.messageService().send(player, "lobby.play.join-failed",
                    Map.of("game", gameId.value()));
        }
    }

    private void handleQueued(List<Player> group, GameId gameId, String mapFilter, boolean ranked, String partyId) {
        Player player = group.get(0);
        // Fleet-wide view: the lobby never has the game plugin loaded.
        if (!core.coordination().allHostedGames().contains(gameId)) {
            core.messageService().send(player, "lobby.play.unknown-game",
                    Map.of("game", gameId.value()));
            return;
        }
        List<String> mapIds = mapFilter == null ? List.of() : List.of(mapFilter);
        var ratings = ratings();
        int rating = groupRating(group, gameId);
        Instant now = Instant.now();
        for (Player member : group) {
            core.coordination().enqueue(new QueueEntry(member.getUniqueId(), member.getName(), gameId, mapIds, now,
                    ranked, rating, group.size() > 1 ? partyId : null, group.size()));
        }
        tellPulled(group, gameId);
        if (ranked) {
            core.messageService().send(player, "core.ranked.queued", Map.of(
                    "game", gameId.value(), "rating", String.valueOf(rating)));
            return;
        }
        core.messageService().send(player, "lobby.play.queued", Map.of(
                "game", gameId.value(),
                "map", mapFilter == null ? "any" : mapFilter));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            // Fleet-wide view, so completion works on a lobby without game plugins.
            return core.coordination().allHostedGames().stream()
                    .map(GameId::value)
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            try {
                GameId g = GameId.of(args[0]);
                List<String> out = new java.util.ArrayList<>(core.maps().forGame(g).stream()
                        .map(m -> m.mapId())
                        .filter(s -> s.startsWith(prefix))
                        .toList());
                var ratings = ratings();
                if (ratings != null && ratings.enabled(g) && "ranked".startsWith(prefix)) {
                    out.add("ranked");
                }
                return out;
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private Optional<Arena> findWaitingArena(GameId gameId, String mapFilter, boolean ranked, int rating, int room) {
        var ratings = ratings();
        return core.arenas().all().stream()
                .filter(a -> a.gameId().equals(gameId))
                // An arena still pasting its map (ALLOCATING) takes players too:
                // they join once it is ready, so friends queueing together
                // don't each get a fresh arena.
                .filter(a -> a.state() == ArenaState.WAITING || a.state() == ArenaState.COUNTDOWN
                        || a.state() == ArenaState.ALLOCATING)
                .filter(a -> a.players().size() + pendingOf(a) + room <= a.maxPlayers())
                .filter(a -> mapFilter == null || a.mapId().map(mapFilter::equalsIgnoreCase).orElse(false))
                .filter(a -> ratings == null || ratings.accepts(a, ranked, rating))
                .max(java.util.Comparator.comparingInt(a -> a.players().size() + pendingOf(a)))
                .map(a -> (Arena) a);
    }

    private int pendingOf(Arena arena) {
        return core.arenas() instanceof me.playbosswar.tea.core.impl.arena.ArenaManagerImpl impl
                ? impl.pendingJoins(arena.id()) : 0;
    }

    private Arena allocate(Player player, GameId gameId, String mapFilter) {
        try {
            return core.arenas().allocate(gameId, mapFilter);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to allocate arena for " + gameId + ": " + e.getMessage());
            core.messageService().send(player, "lobby.play.allocation-failed",
                    Map.of("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return null;
        }
    }
}
