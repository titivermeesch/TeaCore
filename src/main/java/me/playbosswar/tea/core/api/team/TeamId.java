package me.playbosswar.tea.core.api.team;

import java.util.Objects;

/** Non-blank identifier of a {@link TeamDefinition}. */
public record TeamId(String value) {

    public TeamId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank())
            throw new IllegalArgumentException("TeamId must not be blank");
    }

    public static TeamId of(String value) {
        return new TeamId(value);
    }

    /** Returns the bare value, without the record wrapper. */
    @Override
    public String toString() {
        return value;
    }
}
