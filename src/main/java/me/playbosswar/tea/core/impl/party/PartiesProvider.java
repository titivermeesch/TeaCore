package me.playbosswar.tea.core.impl.party;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Parties by AlessioDP. Only loaded when that plugin is enabled. */
final class PartiesProvider implements PartyProvider {

    @Override
    public String name() {
        return "Parties";
    }

    @Override
    public Optional<Party> partyOf(UUID player) {
        PartiesAPI api = Parties.getApi();
        var party = api.getPartyOfPlayer(player);
        if (party == null || party.getLeader() == null || party.getMembers().size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new Party(party.getId().toString(), party.getLeader(), Set.copyOf(party.getMembers())));
    }
}
