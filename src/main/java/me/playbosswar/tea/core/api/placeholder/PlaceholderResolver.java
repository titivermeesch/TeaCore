package me.playbosswar.tea.core.api.placeholder;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves placeholders registered with {@link PlaceholderService} under a
 * namespace, reached as {@code %tea_<namespace>_<key>%}.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /**
     * @param player may be offline, or {@code null} when PlaceholderAPI requests
     *               without a player.
     * @param key    the key without the namespace, e.g. {@code "evolutions"} for
     *               {@code %tea_ascend_evolutions%}.
     * @return the value, or {@code null} if this resolver does not handle the
     *         key, in which case the service tries the next resolver.
     */
    @Nullable
    String resolve(@Nullable OfflinePlayer player, String key);
}
