package me.playbosswar.tea.core.impl.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class PartyService {

    private final Plugin plugin;
    private final Logger logger;
    private final List<PartyProvider> providers = new ArrayList<>();

    public PartyService(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /** Call after all plugins have loaded. */
    public void detect() {
        providers.clear();
        String simulate = System.getProperty("tea.parties.simulate", "");
        if (!simulate.isBlank()) {
            providers.add(new SimulatedPartyProvider(simulate));
            logger.info("Parties: simulated for names starting with " + simulate);
        }
        var pm = plugin.getServer().getPluginManager();
        if (pm.isPluginEnabled("Parties")) {
            add("com.alessiodp.parties.api.Parties", PartiesProvider::new);
        }
        if (pm.isPluginEnabled("Spigot-Party-API-PAF")) {
            add("de.simonsator.partyandfriends.spigot.api.party.PartyManager", PafProvider::new);
        }
    }

    private void add(String probeClass, java.util.function.Supplier<PartyProvider> factory) {
        try {
            Class.forName(probeClass);
            PartyProvider p = factory.get();
            providers.add(p);
            logger.info("Parties: using " + p.name());
        } catch (ClassNotFoundException | LinkageError e) {
            logger.warning("Party plugin found but its API could not be loaded: " + e);
        }
    }

    public boolean active() {
        return !providers.isEmpty() && plugin.getConfig().getBoolean("parties.enabled", true);
    }

    public boolean leaderOnly() {
        return plugin.getConfig().getBoolean("parties.leader-only", true);
    }

    public Optional<PartyProvider.Party> partyOf(UUID player) {
        if (!active()) {
            return Optional.empty();
        }
        for (PartyProvider p : providers) {
            try {
                Optional<PartyProvider.Party> party = p.partyOf(player);
                if (party.isPresent()) {
                    return party;
                }
            } catch (RuntimeException | LinkageError e) {
                logger.fine(() -> p.name() + " lookup failed: " + e);
            }
        }
        return Optional.empty();
    }
}
