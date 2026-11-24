package me.playbosswar.tea.core.impl.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** PlaceholderAPI classes are only touched inside {@link Hook}, so servers without it never load them. */
public final class PapiBridge {

    private PapiBridge() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String apply(Player player, String text) {
        if (text.indexOf('%') < 0 || !available()) {
            return text;
        }
        try {
            return Hook.apply(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    private static final class Hook {
        static String apply(Player player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
