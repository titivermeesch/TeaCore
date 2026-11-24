package me.playbosswar.tea.core.api.arena;

import java.util.Objects;
import java.util.UUID;

/** Identifies an {@link Arena}. */
public record ArenaId(UUID value) {

    public ArenaId {
        Objects.requireNonNull(value, "value");
    }

    public static ArenaId random() {
        return new ArenaId(UUID.randomUUID());
    }

    /** @return the bare uuid */
    @Override
    public String toString() {
        return value.toString();
    }
}
