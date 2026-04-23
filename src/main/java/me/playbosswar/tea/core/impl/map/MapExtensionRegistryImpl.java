package me.playbosswar.tea.core.impl.map;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.map.MapExtension;
import me.playbosswar.tea.core.api.map.MapExtensionKey;
import me.playbosswar.tea.core.api.map.MapExtensionRegistry;

public final class MapExtensionRegistryImpl implements MapExtensionRegistry {

    /**
     * Built-in {@code /tea map} subcommand tokens that third-party extensions
     * must not shadow.
     */
    private static final Set<String> RESERVED_SUBCOMMANDS = Set.of(
            "create", "edit", "pos1", "pos2", "waiting", "addspawn", "clearspawns",
            "status", "min", "max", "save", "discard", "list", "info", "reload", "delete",
            "removeextension");

    private final ConcurrentHashMap<String, MapExtension<?>> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MapExtension<?>> bySubcommand = new ConcurrentHashMap<>();

    @Override
    public void register(MapExtension<?> extension) {
        String sub = extension.subcommand().toLowerCase(Locale.ROOT);
        if (RESERVED_SUBCOMMANDS.contains(sub)) {
            throw new IllegalArgumentException(
                    "Subcommand '" + sub + "' is reserved by TeaCore and cannot be registered as an extension.");
        }
        String id = extension.key().id();
        byId.put(id, extension);
        bySubcommand.put(sub, extension);
    }

    @Override
    public void unregister(String id) {
        MapExtension<?> removed = byId.remove(id);
        if (removed != null) {
            bySubcommand.remove(removed.subcommand().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public Optional<MapExtension<?>> bySubcommand(String subcommand) {
        return Optional.ofNullable(bySubcommand.get(subcommand.toLowerCase(Locale.ROOT)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<MapExtension<T>> byKey(MapExtensionKey<T> key) {
        MapExtension<?> ext = byId.get(key.id());
        if (ext == null || !ext.key().equals(key)) {
            return Optional.empty();
        }
        return Optional.of((MapExtension<T>) ext);
    }

    @Override
    public Collection<MapExtension<?>> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /**
     * Looks up an extension by id without requiring a typed key. Used by
     * persistence code that only has the YAML section name.
     *
     * @param id extension id.
     * @return the extension, or empty if not registered.
     */
    public Optional<MapExtension<?>> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
