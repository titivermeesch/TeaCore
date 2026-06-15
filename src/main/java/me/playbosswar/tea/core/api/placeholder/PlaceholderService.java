package me.playbosswar.tea.core.api.placeholder;

import java.util.Optional;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Registry for TeaCore's PlaceholderAPI integration. TeaCore exposes a single
 * PlaceholderAPI expansion with identifier {@code tea}; everything is reached as
 * {@code %tea_<...>%}.
 *
 * <p>Out of the box TeaCore answers fleet- and player-level placeholders (wins,
 * deaths, balance, current arena, queue position, and so on) and auto-exposes
 * every registered game's stats as {@code %tea_<gameId>_<stat>%}. Games layer on
 * their own extras by registering a {@link PlaceholderResolver} under their game
 * id, e.g. Ascend adding {@code %tea_ascend_evolutions%}.
 *
 * <p>Placeholders resolve even when PlaceholderAPI is not installed - the
 * expansion is simply not registered, but {@link #resolve(OfflinePlayer, String)}
 * still works for internal callers (scoreboards, GUIs).
 */
public interface PlaceholderService {

    /**
     * Registers a resolver under a namespace. Multiple resolvers may share a
     * namespace; they are tried in registration order until one returns a
     * non-null value. A namespace usually matches a {@code GameId}.
     *
     * @param namespace the token after {@code tea_}, e.g. {@code "ascend"}; must
     *                  not be {@code null} or blank.
     * @param resolver  the resolver to register; must not be {@code null}.
     */
    void register(String namespace, PlaceholderResolver resolver);

    /**
     * Removes every resolver registered under a namespace.
     *
     * @param namespace the namespace to clear; must not be {@code null}.
     */
    void unregister(String namespace);

    /**
     * Resolves a full placeholder string (everything after {@code tea_}).
     *
     * @param player the player to render for; may be {@code null} or offline.
     * @param params the placeholder string, e.g. {@code "ascend_evolutions"} or
     *               {@code "wins"}; must not be {@code null}.
     * @return the resolved value, or empty if nothing handled it.
     */
    Optional<String> resolve(@Nullable OfflinePlayer player, String params);
}
