package me.playbosswar.tea.core.impl.dashboard.dto;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Serialized map definition for the dashboard. */
public record MapDto(
        String gameId,
        String mapId,
        @Nullable String schematicFile,
        int minPlayers,
        int maxPlayers,
        int participantSpawnCount,
        boolean hasWaitingSpawn,
        List<String> extensions,
        Map<String, String> extensionDescriptions) {
}
