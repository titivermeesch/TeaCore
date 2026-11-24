package me.playbosswar.tea.core.api.game;

import java.util.Objects;

/** Game type id, e.g. {@code "ascend"}. Must be non-blank. */
public record GameId(String value) {

    public GameId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GameId must not be blank");
        }
    }

    public static GameId of(String value) {
        return new GameId(value);
    }

    /** The raw id, for log lines and messages. */
    @Override
    public String toString() {
        return value;
    }
}
