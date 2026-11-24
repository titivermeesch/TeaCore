package me.playbosswar.tea.core.impl.dashboard.dto;

import org.jetbrains.annotations.Nullable;

/** {@code name} is {@code null} for offline uuids. */
public record PlayerRefDto(String uuid, @Nullable String name) {
}
