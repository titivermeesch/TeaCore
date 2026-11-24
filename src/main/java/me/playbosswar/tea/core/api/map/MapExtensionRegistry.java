package me.playbosswar.tea.core.api.map;

import java.util.Collection;
import java.util.Optional;

/** Registry of {@link MapExtension}s. Games register theirs in {@code onEnable}. */
public interface MapExtensionRegistry {

    /**
     * Replaces any extension with the same {@linkplain MapExtensionKey#id() key id}
     * or subcommand.
     *
     * @throws IllegalArgumentException if the subcommand is a built-in such as
     *                                  {@code "pos1"} or {@code "save"}.
     */
    void register(MapExtension<?> extension);

    void unregister(String id);

    /** Case-insensitive. */
    Optional<MapExtension<?>> bySubcommand(String subcommand);

    <T> Optional<MapExtension<T>> byKey(MapExtensionKey<T> key);

    /** Returns a snapshot. */
    Collection<MapExtension<?>> all();
}
