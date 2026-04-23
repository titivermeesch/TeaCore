package me.playbosswar.tea.core.api.team;

import java.util.Objects;

/**
 * Stable string identifier for a {@link TeamDefinition}.
 *
 * <p>
 * Two {@code TeamId}s are equal if their values are equal. Wraps a non-blank
 * string so
 * compile-time types distinguish team ids from other string identifiers.
 *
 * @param value the underlying id; non-blank and never {@code null}.
 */
public record TeamId(String value) {

    /**
     * Canonical constructor that validates {@code value}.
     *
     * @throws NullPointerException     if {@code value} is {@code null}.
     * @throws IllegalArgumentException if {@code value} is blank.
     */
    public TeamId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank())
            throw new IllegalArgumentException("TeamId must not be blank");
    }

    /**
     * Convenience factory equivalent to {@code new TeamId(value)}.
     *
     * @param value id value; must not be {@code null} or blank.
     * @return a new {@link TeamId}, never {@code null}.
     */
    public static TeamId of(String value) {
        return new TeamId(value);
    }

    /**
     * Returns the underlying id so {@code TeamId} is interchangeable with its
     * string form
     * in log lines and messages.
     *
     * @return the id value.
     */
    @Override
    public String toString() {
        return value;
    }
}
