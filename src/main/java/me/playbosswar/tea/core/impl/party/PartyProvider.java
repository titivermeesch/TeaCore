package me.playbosswar.tea.core.impl.party;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PartyProvider {

    /** {@code members} includes the leader. */
    record Party(String id, UUID leader, Set<UUID> members) { }

    String name();

    /** Only parties with at least one other player. */
    Optional<Party> partyOf(UUID player);
}
