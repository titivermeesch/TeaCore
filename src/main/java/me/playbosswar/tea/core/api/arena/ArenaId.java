package me.playbosswar.tea.core.api.arena;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for an {@link Arena} instance.
 *
 * <p>Wraps a non-null {@link UUID} so compile-time types distinguish arena ids from other
 * uuid-valued identifiers. Two {@code ArenaId}s are equal iff their underlying uuids are
 * equal.
 *
 * @param value the underlying uuid; never {@code null}.
 */
public record ArenaId(UUID value) {

    /**
     * Canonical constructor that validates {@code value}.
     *
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    public ArenaId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Generates a new random arena id.
     *
     * @return a fresh id backed by {@link UUID#randomUUID()}, never {@code null}.
     */
    public static ArenaId random() {
        return new ArenaId(UUID.randomUUID());
    }

    /**
     * Returns the underlying uuid in string form.
     *
     * @return the uuid as a string.
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
