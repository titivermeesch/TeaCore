package me.playbosswar.tea.core.api.map;

/**
 * Typed key for a {@link MapExtension}, usually held in a
 * {@code static final} field.
 *
 * @param id   also the YAML section name; use a stable namespaced id such as
 *             {@code "ascend:evolution-viewer"}
 * @param type used to cast stored values safely on lookup
 */
public record MapExtensionKey<T>(String id, Class<T> type) {

    public static <T> MapExtensionKey<T> of(String id, Class<T> type) {
        return new MapExtensionKey<>(id, type);
    }
}
