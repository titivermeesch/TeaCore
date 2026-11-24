package me.playbosswar.tea.core.api.menu;

/**
 * Something a click can do. Written in YAML as {@code <id>} or
 * {@code <id>:<argument>}; {@code {placeholders}} in the argument are filled
 * from the clicked entry and the menu arguments before the action runs.
 */
@FunctionalInterface
public interface MenuAction {

    /**
     * @param argument the text after the first {@code :}, placeholders already
     *                 filled; empty if there was none.
     */
    void run(MenuClick click, String argument);
}
