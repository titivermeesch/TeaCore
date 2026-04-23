package me.playbosswar.tea.core.api.map;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;
import org.jetbrains.annotations.Nullable;

/**
 * Definition of a single map for a game.
 *
 * <p>
 * Player bounds live here rather than on
 * {@link me.playbosswar.tea.core.api.game.GameDefinition}
 * so different maps of the same game can support different participant counts.
 * Games attach additional game-specific data (ceremony spawns, capture-point
 * lists, boss-entity type, ...) via {@link MapExtension}s registered with the
 * {@link MapExtensionRegistry}; stored values live in {@code extensions},
 * keyed by the extension's id, and are looked up through typed keys using
 * {@link #extension(MapExtensionKey)}. Core treats the map as opaque.
 *
 * @param gameId            game this map belongs to; never {@code null}.
 * @param mapId             id unique within the game; never {@code null}.
 * @param schematicFile     absolute path to a {@code .schem} (WorldEdit) file,
 *                          or {@code null} for a void-only map.
 * @param waitingSpawn      spawn used while players are in the waiting phase;
 *                          falls back to the first participant spawn at
 *                          runtime.
 * @param participantSpawns spawns used for participants once the match goes
 *                          live; defensively copied, must be non-null.
 * @param extensions        game-contributed values keyed by extension id
 *                          (e.g. {@code "ascend:evolution-viewer"});
 *                          defensively copied, {@code null} normalized to an
 *                          empty map.
 * @param minPlayers        minimum participant count to start a match on this
 *                          map; clamped to at least {@code 1}.
 * @param maxPlayers        hard cap on participants; clamped to at least
 *                          {@code minPlayers}.
 */
public record MapDefinition(
        GameId gameId,
        String mapId,
        @Nullable Path schematicFile,
        SpawnPoint waitingSpawn,
        List<SpawnPoint> participantSpawns,
        Map<String, Object> extensions,
        int minPlayers,
        int maxPlayers) {

    /**
     * Canonical constructor that defensively copies collections and clamps the
     * player-count bounds into a valid range.
     */
    public MapDefinition {
        participantSpawns = List.copyOf(participantSpawns);
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
        if (minPlayers < 1)
            minPlayers = 1;
        if (maxPlayers < minPlayers)
            maxPlayers = minPlayers;
    }

    /**
     * Back-compat constructor for maps that declare no extensions.
     *
     * @param gameId            game this map belongs to.
     * @param mapId             map id.
     * @param schematicFile     absolute path to the schematic, or {@code null}
     *                          for a void-only map.
     * @param waitingSpawn      spawn for the waiting phase.
     * @param participantSpawns spawns for live participants.
     * @param minPlayers        minimum participant count to start a match.
     * @param maxPlayers        maximum participant count allowed.
     */
    public MapDefinition(GameId gameId, String mapId, @Nullable Path schematicFile,
            SpawnPoint waitingSpawn, List<SpawnPoint> participantSpawns,
            int minPlayers, int maxPlayers) {
        this(gameId, mapId, schematicFile, waitingSpawn, participantSpawns,
                Map.of(), minPlayers, maxPlayers);
    }

    /**
     * Returns the schematic file path, if this map has one.
     *
     * @return the path wrapped in an {@link Optional}, empty for void-only maps.
     */
    public Optional<Path> schematic() {
        return Optional.ofNullable(schematicFile);
    }

    /**
     * Looks up a game-contributed extension value.
     *
     * @param key typed key the extension was registered under; must not be
     *            {@code null}.
     * @param <T> value type.
     * @return the stored value, or empty if no value has been authored.
     */
    public <T> Optional<T> extension(MapExtensionKey<T> key) {
        Object raw = extensions.get(key.id());
        if (raw == null || !key.type().isInstance(raw)) {
            return Optional.empty();
        }
        return Optional.of(key.type().cast(raw));
    }
}
