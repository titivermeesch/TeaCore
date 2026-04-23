package me.playbosswar.tea.core.impl.dashboard.dto;

/** Serialized shape of a registered game for the dashboard. */
public record GameSummaryDto(
        String id,
        String displayNameKey,
        String teamMode,
        int poolMinAvailable,
        int poolMaxTotal,
        int poolPreloadOnStartup,
        int lifecycleMaxWaitSeconds,
        int lifecycleCountdownSeconds,
        int lifecycleStartingSeconds,
        int lifecycleEndingSeconds,
        int lifecycleMaxMatchSeconds,
        String endMode,
        String mapSelectionMode,
        int mapCount) {
}
