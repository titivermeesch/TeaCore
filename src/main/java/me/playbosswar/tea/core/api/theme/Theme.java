package me.playbosswar.tea.core.api.theme;

import java.util.Set;

/**
 * The active theme's style slots. Values are raw MiniMessage tags such as
 * {@code "<#5fd1ff>"}. Translations usually reference slots by name and let
 * {@link me.playbosswar.tea.core.api.message.MessageService} resolve them.
 */
public interface Theme {

    /** The slot's opening tag, or an empty string for an unknown slot. */
    String tag(String slotName);

    /** Empty when the theme defines no prefix. */
    String prefix();

    /** Returns a snapshot. */
    Set<String> slotNames();
}
