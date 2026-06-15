package me.playbosswar.tea.core.api.placeholder;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a single placeholder key to a value. Registered with the
 * {@link PlaceholderService} under a namespace so it is reached as
 * {@code %tea_<namespace>_<key>%}.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * Resolves one key for a player.
     *
     * @param player the player the placeholder is rendered for; may be
     *               {@code null} (PlaceholderAPI can request with no player) and
     *               may be offline.
     * @param key    the placeholder key with the namespace stripped (e.g. for
     *               {@code %tea_ascend_evolutions%} this is {@code "evolutions"}).
     * @return the resolved value, or {@code null} if this resolver does not
     *         handle the key (the service then tries the next candidate).
     */
    @Nullable
    String resolve(@Nullable OfflinePlayer player, String key);
}
