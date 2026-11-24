package me.playbosswar.tea.core.api.map;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;
import org.jetbrains.annotations.Nullable;

/**
 * A game's map. Player bounds live here, not on the game, so maps of one game
 * can support different player counts. Game-specific data is stored in
 * {@code extensions} and read with {@link #extension(MapExtensionKey)}.
 *
 * @param displayName       {@code null} or blank falls back to {@link #mapId()}
 * @param schematicFile     absolute path to a {@code .schem} file, or {@code null} for a void-only map
 * @param waitingSpawn      falls back to the first participant spawn at runtime
 * @param extensions        keyed by extension id (e.g. {@code "ascend:evolution-viewer"}); {@code null} becomes empty
 * @param minPlayers        clamped to at least {@code 1}
 * @param maxPlayers        clamped to at least {@code minPlayers}
 */
public record MapDefinition(
        GameId gameId,
        String mapId,
        @Nullable String displayName,
        @Nullable Path schematicFile,
        SpawnPoint waitingSpawn,
        List<SpawnPoint> participantSpawns,
        Map<String, Object> extensions,
        int minPlayers,
        int maxPlayers) {

    public MapDefinition {
        participantSpawns = List.copyOf(participantSpawns);
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
        if (displayName != null && displayName.isBlank())
            displayName = null;
        if (minPlayers < 1)
            minPlayers = 1;
        if (maxPlayers < minPlayers)
            maxPlayers = minPlayers;
    }

    public MapDefinition(GameId gameId, String mapId, @Nullable Path schematicFile,
            SpawnPoint waitingSpawn, List<SpawnPoint> participantSpawns,
            Map<String, Object> extensions, int minPlayers, int maxPlayers) {
        this(gameId, mapId, null, schematicFile, waitingSpawn, participantSpawns,
                extensions, minPlayers, maxPlayers);
    }

    public MapDefinition(GameId gameId, String mapId, @Nullable Path schematicFile,
            SpawnPoint waitingSpawn, List<SpawnPoint> participantSpawns,
            int minPlayers, int maxPlayers) {
        this(gameId, mapId, null, schematicFile, waitingSpawn, participantSpawns,
                Map.of(), minPlayers, maxPlayers);
    }

    /** {@link #displayName()}, or {@link #mapId()} when none is set. */
    public String resolvedDisplayName() {
        return displayName == null ? mapId : displayName;
    }

    /** Empty for void-only maps. */
    public Optional<Path> schematic() {
        return Optional.ofNullable(schematicFile);
    }

    /** Empty if no value is set or it is not of the key's type. */
    public <T> Optional<T> extension(MapExtensionKey<T> key) {
        Object raw = extensions.get(key.id());
        if (raw == null || !key.type().isInstance(raw)) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(raw));
    }
}
