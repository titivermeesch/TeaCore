package me.playbosswar.tea.core.impl.arena;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaId;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.World;

public final class ArenaImpl implements Arena {

    private final ArenaId id;
    private final GameId gameId;
    private volatile String mapId;
    /** Set when a player asked for this map: map voting leaves the arena alone. */
    private volatile boolean mapLocked;
    private final int minPlayers;
    private final int maxPlayers;

    private volatile World world;
    /** This arena's own copy of its waiting room, or null (players wait at the map's waiting spawn). */
    private volatile WaitingRooms.Room waitingRoom;

    public WaitingRooms.Room waitingRoom() {
        return waitingRoom;
    }

    void setWaitingRoom(WaitingRooms.Room room) {
        this.waitingRoom = room;
    }
    private volatile ArenaState state = ArenaState.ALLOCATING;
    private final UUID matchId = UUID.randomUUID();
    private volatile int phaseSecondsRemaining = -1;

    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();

    public ArenaImpl(ArenaId id, GameId gameId, String mapId, int minPlayers, int maxPlayers) {
        this.id = id;
        this.gameId = gameId;
        this.mapId = mapId;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    @Override
    public ArenaId id() {
        return id;
    }

    @Override
    public GameId gameId() {
        return gameId;
    }

    @Override
    public Optional<String> mapId() {
        return Optional.ofNullable(mapId);
    }

    /** Map voting: the world is swapped separately. */
    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

    public boolean mapLocked() {
        return mapLocked;
    }

    public void lockMap() {
        this.mapLocked = true;
    }

    @Override
    public UUID matchId() {
        return matchId;
    }

    @Override
    public ArenaState state() {
        return state;
    }

    @Override
    public Optional<World> world() {
        return Optional.ofNullable(world);
    }

    @Override
    public Set<UUID> players() {
        return Set.copyOf(players);
    }

    @Override
    public Set<UUID> spectators() {
        return Set.copyOf(spectators);
    }

    @Override
    public int minPlayers() {
        return minPlayers;
    }

    @Override
    public int maxPlayers() {
        return maxPlayers;
    }

    @Override
    public int phaseSecondsRemaining() {
        return phaseSecondsRemaining;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }

    public void setPhaseSecondsRemaining(int seconds) {
        this.phaseSecondsRemaining = seconds;
    }

    public void decrementPhaseSecondsRemaining() {
        this.phaseSecondsRemaining--;
    }

    public boolean addPlayer(UUID uuid) {
        spectators.remove(uuid);
        return players.add(uuid);
    }

    public boolean addSpectator(UUID uuid) {
        players.remove(uuid);
        return spectators.add(uuid);
    }

    public boolean removePlayer(UUID uuid) {
        boolean removed = players.remove(uuid);
        removed |= spectators.remove(uuid);
        return removed;
    }

    public Set<UUID> allOccupants() {
        Set<UUID> out = ConcurrentHashMap.newKeySet();
        out.addAll(players);
        out.addAll(spectators);
        return out;
    }
}
