package me.playbosswar.tea.core.api.placeholder;

import java.util.Optional;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * TeaCore's PlaceholderAPI expansion, identifier {@code tea}, reached as
 * {@code %tea_<...>%}.
 *
 * <p>Built in: player-level placeholders (wins, balance, current arena, queue
 * position, ...) and every registered game's stats as
 * {@code %tea_<gameId>_<stat>%}. Games add extras by registering a
 * {@link PlaceholderResolver} under their game id.
 *
 * <p>{@link #resolve(OfflinePlayer, String)} works even without PlaceholderAPI
 * installed.
 */
public interface PlaceholderService {

    /**
     * Resolvers sharing a namespace are tried in registration order until one
     * returns non-null.
     *
     * @param namespace the token after {@code tea_}, e.g. {@code "ascend"}; not blank.
     */
    void register(String namespace, PlaceholderResolver resolver);

    /** Removes every resolver in the namespace. */
    void unregister(String namespace);

    /**
     * @param player may be {@code null} or offline.
     * @param params everything after {@code tea_}, e.g. {@code "ascend_evolutions"}.
     * @return empty if nothing handled it.
     */
    Optional<String> resolve(@Nullable OfflinePlayer player, String params);
}
