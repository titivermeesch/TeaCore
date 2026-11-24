package me.playbosswar.tea.core.api.team;

import java.util.Objects;

/**
 * A team declared on {@link me.playbosswar.tea.core.api.game.GameDefinition}.
 *
 * @param displayNameKey translation key for the team's name.
 * @param colorStyleSlot name of a {@link me.playbosswar.tea.core.api.theme.Theme}
 *                       slot, e.g. {@code "accent"} or {@code "team.red"},
 *                       resolved at render time.
 * @param maxSize        must be {@code > 0}.
 */
public record TeamDefinition(
        TeamId id,
        String displayNameKey,
        String colorStyleSlot,
        int maxSize) {

    public TeamDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(colorStyleSlot, "colorStyleSlot");
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be > 0");
    }
}
