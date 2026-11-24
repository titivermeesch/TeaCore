package me.playbosswar.tea.core.api.team;

import java.util.Set;
import java.util.UUID;

/** A team inside one arena, created from a {@link TeamDefinition}. */
public interface Team {

    TeamId id();

    /** Translation key. */
    String displayNameKey();

    /** Theme style slot used to colour the team name. */
    String colorStyleSlot();

    /** Live view that changes as members join and leave. */
    Set<UUID> members();

    /** @return always greater than 0 */
    int maxSize();
}
