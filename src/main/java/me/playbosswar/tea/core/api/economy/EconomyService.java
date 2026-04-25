package me.playbosswar.tea.core.api.economy;

import java.util.UUID;

/**
 * Thin wrapper around the server's economy provider.
 *
 * <p>
 * TeaCore delegates all money operations to Vault; whatever plugin owns the
 * Vault {@code Economy} service is the real source of truth (EssentialsEco,
 * CMI, an SQL-backed economy plugin, etc.). This service exists so TeaCore and
 * Tea games can read and write balances without each of them wiring the Vault
 * API themselves.
 *
 * <p>
 * Vault is a hard dependency of TeaCore, so the plugin is always on the
 * classpath. If no plugin has registered an implementation of Vault's
 * {@code Economy} service, {@link #isAvailable()} returns {@code false} and
 * every mutating call returns {@code false} / {@code 0}.
 */
public interface EconomyService {

    /**
     * Whether an economy provider is registered with Vault.
     *
     * <p>
     * {@code false} means Vault is installed but no plugin has registered a
     * concrete {@code Economy} implementation yet. Callers should treat this as
     * "economy features are unavailable" — refuse kit purchases, hide balance,
     * etc.
     *
     * @return {@code true} when balance reads and writes will go through to a
     *         real economy provider.
     */
    boolean isAvailable();

    /**
     * Returns a player's current balance, or {@code 0} if economy is unavailable
     * or the provider has no record for that player.
     *
     * @param playerId player to look up; must not be {@code null}.
     * @return the balance in the provider's native currency units.
     */
    double balance(UUID playerId);

    /**
     * Checks whether a player can afford an amount.
     *
     * @param playerId player to look up; must not be {@code null}.
     * @param amount   amount to check; must be {@code >= 0}.
     * @return {@code true} only if the economy is available AND the player's
     *         balance is {@code >= amount}.
     */
    boolean has(UUID playerId, double amount);

    /**
     * Adds money to a player's balance.
     *
     * @param playerId recipient; must not be {@code null}.
     * @param amount   amount to deposit; must be {@code >= 0}.
     * @return {@code true} if the deposit succeeded. Returns {@code false} when
     *         the economy is unavailable, the amount is negative, or the Vault
     *         provider reports failure.
     */
    boolean deposit(UUID playerId, double amount);

    /**
     * Removes money from a player's balance.
     *
     * @param playerId payer; must not be {@code null}.
     * @param amount   amount to withdraw; must be {@code >= 0}.
     * @return {@code true} if the withdrawal succeeded. Returns {@code false}
     *         when the economy is unavailable, the amount is negative, or the
     *         player has insufficient funds.
     */
    boolean withdraw(UUID playerId, double amount);

    /**
     * Sets a player's balance to an exact amount.
     *
     * <p>
     * Vault has no native "set balance" operation; this is emulated by reading
     * the current balance and issuing a compensating deposit or withdrawal.
     * The operation is not atomic, so a concurrent transaction between the
     * read and the write can cause a minor skew; the provider plugin's own
     * serialization usually prevents this in practice.
     *
     * @param playerId player whose balance to replace; must not be {@code null}.
     * @param amount   target balance; must be {@code >= 0}.
     * @return {@code true} if the adjustment succeeded.
     */
    boolean set(UUID playerId, double amount);

    /**
     * Formats an amount for display using the Vault provider's configured
     * currency name and formatter.
     *
     * <p>
     * Falls back to {@code String.valueOf(amount)} if the economy is
     * unavailable.
     *
     * @param amount amount to format.
     * @return a human-readable string, never {@code null}.
     */
    String format(double amount);
}
