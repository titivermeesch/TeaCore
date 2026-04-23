package me.playbosswar.tea.core.impl.map;

import java.util.Optional;
import me.playbosswar.tea.core.api.map.MapDraftHandle;
import me.playbosswar.tea.core.api.map.MapExtensionKey;

/**
 * Concrete {@link MapDraftHandle} that reads/writes the extension identified by
 * a key on an in-flight {@link MapDraft}.
 */
public final class DraftHandle<T> implements MapDraftHandle<T> {

    private final MapDraft draft;
    private final MapExtensionKey<T> key;

    public DraftHandle(MapDraft draft, MapExtensionKey<T> key) {
        this.draft = draft;
        this.key = key;
    }

    @Override
    public Optional<T> current() {
        return draft.extension(key);
    }

    @Override
    public void set(T value) {
        draft.setExtension(key, value);
    }

    @Override
    public void clear() {
        draft.clearExtension(key.id());
    }
}
