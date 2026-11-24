package me.playbosswar.tea.core.impl.economy;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.economy.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** The Vault provider is resolved once at construction; without one, mutating calls are no-ops that return false. */
public final class VaultEconomyService implements EconomyService {

    private final Logger logger;
    private final Economy economy;

    public VaultEconomyService(Logger logger) {
        this.logger = logger;
        this.economy = resolveProvider(logger);
    }

    private static Economy resolveProvider(Logger logger) {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("Vault is installed but no economy provider is registered. "
                    + "Install an economy plugin (EssentialsEco, CMI, etc.) or kit purchases and "
                    + "/tea economy commands will be disabled.");
            return null;
        }
        logger.info("Economy: using Vault provider '" + rsp.getProvider().getName() + "'");
        return rsp.getProvider();
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public double balance(UUID playerId) {
        if (economy == null || playerId == null)
            return 0;
        try {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            return economy.getBalance(target);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Vault balance lookup failed for " + playerId, t);
            return 0;
        }
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        if (economy == null || playerId == null || amount < 0)
            return false;
        try {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            return economy.has(target, amount);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Vault 'has' check failed for " + playerId, t);
            return false;
        }
    }

    @Override
    public boolean deposit(UUID playerId, double amount) {
        if (economy == null || playerId == null || amount < 0)
            return false;
        try {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            EconomyResponse r = economy.depositPlayer(target, amount);
            return r != null && r.transactionSuccess();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Vault deposit failed for " + playerId, t);
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (economy == null || playerId == null || amount < 0)
            return false;
        try {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            EconomyResponse r = economy.withdrawPlayer(target, amount);
            return r != null && r.transactionSuccess();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Vault withdraw failed for " + playerId, t);
            return false;
        }
    }

    @Override
    public boolean set(UUID playerId, double amount) {
        if (economy == null || playerId == null || amount < 0)
            return false;
        double current = balance(playerId);
        double delta = amount - current;
        if (delta == 0)
            return true;
        return delta > 0 ? deposit(playerId, delta) : withdraw(playerId, -delta);
    }

    @Override
    public String format(double amount) {
        if (economy == null)
            return String.valueOf(amount);
        try {
            return economy.format(amount);
        } catch (Throwable t) {
            return String.valueOf(amount);
        }
    }
}
