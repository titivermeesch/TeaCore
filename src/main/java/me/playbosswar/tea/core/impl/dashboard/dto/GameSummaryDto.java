package me.playbosswar.tea.core.impl.dashboard.dto;

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
        int mapCount,
        /** Servers hosting this game; empty means this server. Settings above are only known for local games. */
        java.util.List<String> hosts) {
}
