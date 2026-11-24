package me.playbosswar.tea.core.impl.dashboard.dto;

import java.util.List;

/** Timestamps are ISO-8601 UTC strings. */
public record QueueEntryDto(
        String playerUuid,
        String playerName,
        String gameId,
        List<String> mapIds,
        String queuedAt,
        int position) {
}
