package me.playbosswar.tea.core.impl.dashboard.dto;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Serialized profile + per-game stats for a single player. */
public record PlayerProfileDto(
        String uuid,
        @Nullable String name,
        boolean online,
        @Nullable String firstSeen,
        @Nullable String lastSeen,
        @Nullable String locale,
        Map<String, Map<String, String>> statsByGame) {
}
