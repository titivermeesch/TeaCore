package me.playbosswar.tea.core.api.game;

import java.util.Objects;

/**
 * Stable string identifier for a registered game type (e.g. {@code "ascend"}).
 *
 * <p>Wraps a non-blank string so compile-time types can distinguish a game id from other
 * string-valued identifiers. Two {@code GameId}s are equal iff their values are equal.
 *
 * @param value the underlying id; non-blank and never {@code null}.
 */
public record GameId(String value) {

    /**
     * Canonical constructor that validates {@code value}.
     *
     * @throws NullPointerException     if {@code value} is {@code null}.
     * @throws IllegalArgumentException if {@code value} is blank.
     */
    public GameId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GameId must not be blank");
        }
    }

    /**
     * Convenience factory equivalent to {@code new GameId(value)}.
     *
     * @param value id value; must not be {@code null} or blank.
     * @return a new {@link GameId}, never {@code null}.
     */
    public static GameId of(String value) {
        return new GameId(value);
    }

    /**
     * Returns the underlying id so {@code GameId} is interchangeable with its string form
     * in log lines and messages.
     *
     * @return the id value.
     */
    @Override
    public String toString() {
        return value;
    }
}
