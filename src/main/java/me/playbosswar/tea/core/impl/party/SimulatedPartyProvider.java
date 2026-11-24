package me.playbosswar.tea.core.impl.party;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Test stand-in for a party plugin, turned on with {@code -Dtea.parties.simulate=<prefix>}.
 * Online players named {@code <prefix><group>_<n>} form one party per group;
 * the one ending in {@code _0} leads it.
 */
final class SimulatedPartyProvider implements PartyProvider {

    private final String prefix;

    SimulatedPartyProvider(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String name() {
        return "simulated parties (" + prefix + ")";
    }

    @Override
    public Optional<Party> partyOf(UUID uuid) {
        Player me = Bukkit.getPlayer(uuid);
        if (me == null || !me.getName().startsWith(prefix) || me.getName().indexOf('_') < 0) {
            return Optional.empty();
        }
        String group = me.getName().substring(0, me.getName().lastIndexOf('_'));
        Set<UUID> members = new HashSet<>();
        UUID leader = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n.lastIndexOf('_') > 0 && n.substring(0, n.lastIndexOf('_')).equals(group)) {
                members.add(p.getUniqueId());
                if (n.endsWith("_0")) {
                    leader = p.getUniqueId();
                }
            }
        }
        return members.size() < 2 || leader == null ? Optional.empty()
                : Optional.of(new Party(group, leader, Set.copyOf(members)));
    }
}
