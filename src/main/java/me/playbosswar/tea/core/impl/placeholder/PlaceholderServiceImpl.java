package me.playbosswar.tea.core.impl.placeholder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import me.playbosswar.tea.core.api.placeholder.PlaceholderResolver;
import me.playbosswar.tea.core.api.placeholder.PlaceholderService;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderServiceImpl implements PlaceholderService {

    private final Map<String, List<PlaceholderResolver>> namespaced = new ConcurrentHashMap<>();
    private final List<PlaceholderResolver> global = new CopyOnWriteArrayList<>();

    @Override
    public void register(String namespace, PlaceholderResolver resolver) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        namespaced.computeIfAbsent(namespace.toLowerCase(), k -> new CopyOnWriteArrayList<>()).add(resolver);
    }

    @Override
    public void unregister(String namespace) {
        namespaced.remove(namespace.toLowerCase());
    }

    /**
     * Registers a resolver with no namespace, consulted (in registration order)
     * for any {@code params} no namespaced resolver handled. TeaCore's own
     * built-ins register here; not part of the public API.
     */
    public void registerGlobal(PlaceholderResolver resolver) {
        global.add(resolver);
    }

    @Override
    public Optional<String> resolve(@Nullable OfflinePlayer player, String params) {
        String lower = params.toLowerCase();
        for (Map.Entry<String, List<PlaceholderResolver>> entry : namespaced.entrySet()) {
            String ns = entry.getKey();
            if (!lower.equals(ns) && !lower.startsWith(ns + "_")) {
                continue;
            }
            String key = lower.equals(ns) ? "" : params.substring(ns.length() + 1);
            String value = first(entry.getValue(), player, key);
            if (value != null) {
                return Optional.of(value);
            }
        }
        String value = first(global, player, params);
        return Optional.ofNullable(value);
    }

    @Nullable
    private static String first(List<PlaceholderResolver> resolvers, @Nullable OfflinePlayer player, String key) {
        for (PlaceholderResolver resolver : resolvers) {
            String value = resolver.resolve(player, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
