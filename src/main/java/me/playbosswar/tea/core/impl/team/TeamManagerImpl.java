package me.playbosswar.tea.core.impl.team;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.game.TeamMode;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.api.team.TeamDefinition;
import me.playbosswar.tea.core.api.team.TeamId;
import me.playbosswar.tea.core.api.team.TeamManager;

public final class TeamManagerImpl implements TeamManager {

    private final ArenaManager arenaManager;
    private final GameRegistry gameRegistry;
    private final ConcurrentHashMap<ArenaId, LinkedHashMap<TeamId, TeamImpl>> arenaTeams = new ConcurrentHashMap<>();

    private java.util.function.Function<UUID, java.util.Set<UUID>> partyMates = u -> java.util.Set.of();

    public void setPartyMates(java.util.function.Function<UUID, java.util.Set<UUID>> partyMates) {
        this.partyMates = partyMates;
    }

    public TeamManagerImpl(ArenaManager arenaManager, GameRegistry gameRegistry) {
        this.arenaManager = arenaManager;
        this.gameRegistry = gameRegistry;
    }

    @Override
    public synchronized Team assign(ArenaId arenaId, UUID playerUuid) {
        Arena arena = arenaManager.get(arenaId)
                .orElseThrow(() -> new IllegalStateException("Unknown arena: " + arenaId));
        GameDefinition def = gameRegistry.get(arena.gameId())
                .orElseThrow(() -> new IllegalStateException("Unknown game: " + arena.gameId()));

        LinkedHashMap<TeamId, TeamImpl> teams = arenaTeams.computeIfAbsent(arenaId, id -> new LinkedHashMap<>());

        if (def.teamMode() == TeamMode.FFA) {
            TeamId solo = new TeamId("ffa-" + playerUuid);
            TeamImpl team = new TeamImpl(solo, "core.team.ffa.name", "accent", 1);
            team.add(playerUuid);
            teams.put(solo, team);
            return team;
        }

        if (teams.isEmpty()) {
            int limit = teamLimits.getOrDefault(arenaId, Integer.MAX_VALUE);
            for (TeamDefinition td : declaredTeams(def)) {
                if (teams.size() >= limit) {
                    break;
                }
                teams.put(td.id(), new TeamImpl(td.id(), td.displayNameKey(), td.colorStyleSlot(), td.maxSize()));
            }
        }

        TeamImpl target = null;
        for (UUID mate : partyMates.apply(playerUuid)) {
            for (TeamImpl t : teams.values()) {
                if (t.members().contains(mate) && t.size() < t.maxSize()) {
                    target = t;
                }
            }
        }
        if (target == null) {
            target = pickSmallest(teams.values());
        }
        if (target == null || !target.add(playerUuid)) {
            throw new IllegalStateException("No team slot available in arena " + arenaId);
        }
        return target;
    }

    @Override
    public Optional<Team> teamOf(ArenaId arenaId, UUID playerUuid) {
        LinkedHashMap<TeamId, TeamImpl> teams = arenaTeams.get(arenaId);
        if (teams == null)
            return Optional.empty();
        for (TeamImpl team : teams.values()) {
            if (team.members().contains(playerUuid))
                return Optional.of(team);
        }
        return Optional.empty();
    }

    @Override
    public Collection<Team> teamsFor(ArenaId arenaId) {
        LinkedHashMap<TeamId, TeamImpl> teams = arenaTeams.get(arenaId);
        if (teams == null)
            return Collections.emptyList();
        return Collections.unmodifiableCollection(teams.values());
    }

    @Override
    public synchronized void remove(ArenaId arenaId, UUID playerUuid) {
        LinkedHashMap<TeamId, TeamImpl> teams = arenaTeams.get(arenaId);
        if (teams == null)
            return;
        teams.values().forEach(t -> t.remove(playerUuid));
        teams.values().removeIf(t -> t.id().value().startsWith("ffa-") && t.size() == 0);
    }

    @Override
    public void resetArena(ArenaId arenaId) {
        arenaTeams.remove(arenaId);
        teamLimits.remove(arenaId);
    }

    private final ConcurrentHashMap<ArenaId, Integer> teamLimits = new ConcurrentHashMap<>();

    @Override
    public void limitTeams(ArenaId arenaId, int count) {
        teamLimits.put(arenaId, Math.max(1, count));
    }

    @Override
    public boolean isSingleTeam(ArenaId arenaId) {
        Arena arena = arenaManager.get(arenaId).orElse(null);
        if (arena == null)
            return false;
        GameDefinition def = gameRegistry.get(arena.gameId()).orElse(null);
        if (def == null)
            return false;
        if (def.teamMode() == TeamMode.FFA)
            return true;
        return declaredTeams(def).size() <= 1;
    }

    private static TeamImpl pickSmallest(Collection<TeamImpl> teams) {
        TeamImpl best = null;
        int bestSize = Integer.MAX_VALUE;
        for (TeamImpl t : teams) {
            if (t.size() < t.maxSize() && t.size() < bestSize) {
                best = t;
                bestSize = t.size();
            }
        }
        return best;
    }

    private static java.util.List<TeamDefinition> declaredTeams(GameDefinition def) {
        return def.teams() == null ? java.util.List.of() : def.teams();
    }
}
