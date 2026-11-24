package me.playbosswar.tea.core.impl.party;

import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayer;
import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayerManager;
import de.simonsator.partyandfriends.spigot.api.party.PartyManager;
import de.simonsator.partyandfriends.spigot.api.party.PlayerParty;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Party and Friends (through its Spigot party API). Only loaded when that plugin is enabled. */
final class PafProvider implements PartyProvider {

    @Override
    public String name() {
        return "PartyAndFriends";
    }

    @Override
    public Optional<Party> partyOf(UUID player) {
        PAFPlayer me = PAFPlayerManager.getInstance().getPlayer(player);
        PlayerParty party = me == null ? null : PartyManager.getInstance().getParty(me);
        if (party == null || party.getLeader() == null) {
            return Optional.empty();
        }
        Set<UUID> members = new HashSet<>();
        for (PAFPlayer p : party.getAllPlayers()) {
            members.add(p.getUniqueId());
        }
        if (members.size() < 2) {
            return Optional.empty();
        }
        UUID leader = party.getLeader().getUniqueId();
        return Optional.of(new Party(leader.toString(), leader, Set.copyOf(members)));
    }
}
