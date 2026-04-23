package me.playbosswar.tea.core.api.team;

import java.util.Set;
import java.util.UUID;

/**
 * Runtime view of a team within a single arena.
 *
 * <p>
 * Teams are derived from a {@link TeamDefinition} and managed by
 * {@link TeamManager}.
 * The membership set reflects the current state and should not be relied upon
 * to be
 * immutable.
 */
public interface Team {

    /**
     * Returns the team's stable identifier.
     *
     * @return the id, never {@code null}.
     */
    TeamId id();

    /**
     * Returns the translation key for the team's display name.
     *
     * @return the key, never {@code null}.
     */
    String displayNameKey();

    /**
     * Returns the theme style slot used to color the team name.
     *
     * @return the slot name, never {@code null}.
     */
    String colorStyleSlot();

    /**
     * Returns the current member uuids.
     *
     * @return a set of member uuids, never {@code null}.
     */
    Set<UUID> members();

    /**
     * Returns the maximum number of players allowed on this team.
     *
     * @return the maximum, always {@code > 0}.
     */
    int maxSize();
}
