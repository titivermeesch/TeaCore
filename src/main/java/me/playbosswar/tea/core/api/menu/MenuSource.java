package me.playbosswar.tea.core.api.menu;

import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Supplies the entries of a menu list area. Each entry becomes one item built
 * from the list's item template, with the entry's values filling its
 * {@code {placeholders}}.
 */
@FunctionalInterface
public interface MenuSource {

    /**
     * @param args the arguments the menu was opened with, e.g. {@code game=fireball}
     * @return the entries in display order
     */
    List<MenuEntry> entries(Player viewer, Map<String, String> args);
}
