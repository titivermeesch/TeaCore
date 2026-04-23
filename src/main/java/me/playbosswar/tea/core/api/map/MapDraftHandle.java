package me.playbosswar.tea.core.api.map;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Typed accessor a {@link MapExtension} uses to read and write its own value
 * on the currently-active authoring draft.
 *
 * <p>
 * Scoped to a single extension key - handlers cannot reach across to other
 * extensions through this type. TeaCore wires the handle so the backing draft
 * is whichever one the admin is currently editing; extensions do not need to
 * look it up themselves.
 *
 * @param <T> the extension's value type.
 */
public interface MapDraftHandle<T> {

    /**
     * Returns the currently-stored value for this extension on the draft.
     *
     * @return the value, or empty when nothing has been set yet.
     */
    Optional<T> current();

    /**
     * Stores a value on the draft, overwriting any previous entry.
     *
     * @param value value to store; must not be {@code null}.
     */
    void set(T value);

    /**
     * Removes any stored value for this extension.
     */
    void clear();

    /**
     * Returns a short summary of the current value for {@code /tea map status}.
     *
     * @param value current value or {@code null}; extensions override to give a
     *              nicer rendering than {@link Object#toString()}.
     * @return a one-line description.
     */
    default String describe(@Nullable T value) {
        return value == null ? "-" : value.toString();
    }
}
