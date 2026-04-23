package me.playbosswar.tea.core.api.map;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry of {@link MapExtension}s contributed by game plugins.
 *
 * <p>
 * Games call {@link #register(MapExtension)} in {@code onEnable} to plug their
 * extensions into TeaCore's map authoring, persistence, and lookup pipelines.
 * TeaCore consults the registry when dispatching {@code /tea map <subcommand>}
 * commands, when serializing a draft to YAML, and when rehydrating a stored
 * map definition.
 */
public interface MapExtensionRegistry {

    /**
     * Registers an extension.
     *
     * <p>
     * Overwrites any previous registration with the same
     * {@linkplain MapExtensionKey#id() key id} or subcommand.
     *
     * @param extension the extension to register; must not be {@code null}.
     * @throws IllegalArgumentException if the subcommand conflicts with a
     *                                  reserved TeaCore built-in
     *                                  (e.g. {@code "pos1"}, {@code "save"}).
     */
    void register(MapExtension<?> extension);

    /**
     * Removes a registered extension by key id.
     *
     * @param id the extension id; must not be {@code null}.
     */
    void unregister(String id);

    /**
     * Looks up an extension by its {@code /tea map} subcommand token.
     *
     * @param subcommand subcommand token (case-insensitive); must not be
     *                   {@code null}.
     * @return the extension, or empty if no registration exists.
     */
    Optional<MapExtension<?>> bySubcommand(String subcommand);

    /**
     * Looks up an extension by its typed key.
     *
     * @param key the key to look up; must not be {@code null}.
     * @param <T> the extension's value type.
     * @return the extension, or empty if nothing is registered for that key.
     */
    <T> Optional<MapExtension<T>> byKey(MapExtensionKey<T> key);

    /**
     * Returns every registered extension.
     *
     * @return a snapshot collection, never {@code null}.
     */
    Collection<MapExtension<?>> all();
}
