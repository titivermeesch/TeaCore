package me.playbosswar.tea.core.api.map;

/**
 * A typed identifier for a {@link MapExtension}.
 *
 * <p>
 * Games declare one key per extension (typically as a {@code public static
 * final} field) and use it to read values off a {@link MapDefinition} or to
 * write them via {@link MapDraftHandle}. The {@code id} doubles as the YAML
 * section name, so it should be a stable namespace-qualified string like
 * {@code "ascend:evolution-viewer"}.
 *
 * @param id   unique id for this extension (appears in map YAML); never
 *             {@code null}.
 * @param type runtime class for the extension's value, used for safe casts on
 *             lookup; never {@code null}.
 * @param <T>  value type stored under this key.
 */
public record MapExtensionKey<T>(String id, Class<T> type) {

    /**
     * Convenience factory.
     *
     * @param id   extension id; must not be {@code null}.
     * @param type value class; must not be {@code null}.
     * @param <T>  value type.
     * @return a new key, never {@code null}.
     */
    public static <T> MapExtensionKey<T> of(String id, Class<T> type) {
        return new MapExtensionKey<>(id, type);
    }
}
