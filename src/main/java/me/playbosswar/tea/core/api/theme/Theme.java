package me.playbosswar.tea.core.api.theme;

import java.util.Set;

/**
 * Access to the active theme's named style slots and prefix.
 *
 * <p>
 * Values are raw MiniMessage tag strings such as {@code "<#5fd1ff>"} or
 * {@code "<bold><white>"}. Consumers typically do not call this interface
 * directly;
 * instead, translation entries reference slots by name and the
 * {@link me.playbosswar.tea.core.api.message.MessageService} resolves them at
 * render time.
 */
public interface Theme {

    /**
     * Returns the MiniMessage opening-tag value for a slot.
     *
     * @param slotName name of the slot; must not be {@code null}.
     * @return the tag string, or an empty string if no slot is defined under that
     *         name.
     */
    String tag(String slotName);

    /**
     * Returns the rendered prefix applied before themed messages.
     *
     * @return the prefix, never {@code null}; empty when the theme defines none.
     */
    String prefix();

    /**
     * Returns all defined slot names.
     *
     * @return a snapshot set of slot names, never {@code null}.
     */
    Set<String> slotNames();
}
