package me.playbosswar.tea.core.api.map;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes one {@link MapExtension}'s value on the draft the admin is
 * currently editing. Cannot reach other extensions' values.
 */
public interface MapDraftHandle<T> {

    Optional<T> current();

    void set(T value);

    void clear();

    /** One-line summary for {@code /tea map status}. Override for nicer output than {@code toString()}. */
    default String describe(@Nullable T value) {
        return value == null ? "-" : value.toString();
    }
}
