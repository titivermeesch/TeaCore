package me.playbosswar.tea.core.api.team;

import java.util.Objects;

/**
 * Declarative description of a team in a game.
 *
 * <p>
 * Teams are declared statically on
 * {@link me.playbosswar.tea.core.api.game.GameDefinition}. The color is a theme
 * style slot
 * name (e.g. {@code "accent"} or a game-specific slot like {@code "team.red"})
 * resolved
 * via the active {@link me.playbosswar.tea.core.api.theme.Theme} at render
 * time.
 *
 * @param id             stable identifier; never {@code null}.
 * @param displayNameKey translation key for the team's display name; never
 *                       {@code null}.
 * @param colorStyleSlot theme style slot used to color the team name; never
 *                       {@code null}.
 * @param maxSize        maximum number of players assignable to the team; must
 *                       be
 *                       {@code > 0}.
 */
public record TeamDefinition(
        TeamId id,
        String displayNameKey,
        String colorStyleSlot,
        int maxSize) {

    /**
     * Canonical constructor that validates required fields.
     *
     * @throws NullPointerException     if any required field is {@code null}.
     * @throws IllegalArgumentException if {@code maxSize} is not positive.
     */
    public TeamDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(colorStyleSlot, "colorStyleSlot");
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be > 0");
    }
}
