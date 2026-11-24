package me.playbosswar.tea.core.api.economy;

import java.util.UUID;

/**
 * Wrapper around the Vault {@code Economy} provider. When no economy plugin
 * has registered with Vault, {@link #isAvailable()} is {@code false} and every
 * call returns {@code false} or {@code 0}.
 */
public interface EconomyService {

    /** When {@code false}, hide balances and refuse purchases. */
    boolean isAvailable();

    /** {@code 0} if the economy is unavailable or has no record for the player. */
    double balance(UUID playerId);

    boolean has(UUID playerId, double amount);

    /** @return {@code false} if the economy is unavailable, the amount is negative, or the provider refuses */
    boolean deposit(UUID playerId, double amount);

    /** @return {@code false} if the economy is unavailable, the amount is negative, or funds are insufficient */
    boolean withdraw(UUID playerId, double amount);

    /**
     * Emulated with a deposit or withdrawal of the difference, since Vault has no
     * set operation. Not atomic: a concurrent transaction can skew the result.
     */
    boolean set(UUID playerId, double amount);

    /** Uses the provider's currency format; {@code String.valueOf(amount)} if unavailable. */
    String format(double amount);
}
