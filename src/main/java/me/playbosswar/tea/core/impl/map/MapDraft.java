package me.playbosswar.tea.core.impl.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapExtensionKey;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import org.bukkit.Location;

/**
 * Mutable authoring state for a map. All coordinates (pos1, pos2, waitingSpawn,
 * participantSpawns,
 * extension values) are ABSOLUTE positions in the live edit world. On save,
 * they're converted
 * into deltas from the region's min-corner, and a fresh schematic is captured
 * between pos1 and
 * pos2.
 */
public final class MapDraft {

    private final UUID ownerUuid;
    private final GameId gameId;
    private final String mapId;
    private final String editWorldName;
    private final Location preEditLocation;

    private Location pos1;
    private Location pos2;
    private SpawnPoint waitingSpawn; // absolute in edit world
    private final List<SpawnPoint> participantSpawns = new ArrayList<>(); // absolute in edit world
    private final Map<String, Object> extensions = new LinkedHashMap<>();
    private int minPlayers = 2;
    private int maxPlayers = 16;

    public MapDraft(UUID ownerUuid, GameId gameId, String mapId,
            String editWorldName, Location preEditLocation) {
        this.ownerUuid = ownerUuid;
        this.gameId = gameId;
        this.mapId = mapId;
        this.editWorldName = editWorldName;
        this.preEditLocation = preEditLocation;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public GameId gameId() {
        return gameId;
    }

    public String mapId() {
        return mapId;
    }

    public String editWorldName() {
        return editWorldName;
    }

    public Location preEditLocation() {
        return preEditLocation;
    }

    public Optional<Location> pos1() {
        return Optional.ofNullable(pos1);
    }

    public Optional<Location> pos2() {
        return Optional.ofNullable(pos2);
    }

    public Optional<SpawnPoint> waitingSpawn() {
        return Optional.ofNullable(waitingSpawn);
    }

    public List<SpawnPoint> participantSpawns() {
        return List.copyOf(participantSpawns);
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public void setPos1(Location loc) {
        this.pos1 = loc;
    }

    public void setPos2(Location loc) {
        this.pos2 = loc;
    }

    public void setWaitingSpawn(SpawnPoint s) {
        this.waitingSpawn = s;
    }

    public void addParticipantSpawn(SpawnPoint s) {
        this.participantSpawns.add(s);
    }

    public void clearParticipantSpawns() {
        this.participantSpawns.clear();
    }

    public void setMinPlayers(int v) {
        this.minPlayers = Math.max(1, v);
    }

    public void setMaxPlayers(int v) {
        this.maxPlayers = Math.max(minPlayers, v);
    }

    /**
     * Returns an unmodifiable snapshot of extension values keyed by extension id.
     */
    public Map<String, Object> extensions() {
        return Collections.unmodifiableMap(extensions);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> extension(MapExtensionKey<T> key) {
        Object raw = extensions.get(key.id());
        if (raw == null || !key.type().isInstance(raw))
            return Optional.empty();
        return Optional.of((T) raw);
    }

    public <T> void setExtension(MapExtensionKey<T> key, T value) {
        extensions.put(key.id(), value);
    }

    public void setRawExtension(String id, Object value) {
        extensions.put(id, value);
    }

    public void clearExtension(String id) {
        extensions.remove(id);
    }

    public int removeParticipantSpawnNear(double x, double y, double z, double radius) {
        double r2 = radius * radius;
        int before = participantSpawns.size();
        participantSpawns.removeIf(s -> {
            double dx = s.x() - x;
            double dy = s.y() - y;
            double dz = s.z() - z;
            return (dx * dx + dy * dy + dz * dz) <= r2;
        });
        return before - participantSpawns.size();
    }
}
