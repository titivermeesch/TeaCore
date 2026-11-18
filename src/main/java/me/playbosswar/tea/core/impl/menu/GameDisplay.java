package me.playbosswar.tea.core.impl.menu;

import java.util.Locale;
import java.util.Map;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.game.GameDefinition;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.message.MessageService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * A game's display name and icon, used by every menu, sidebar and placeholder:
 * {@code game-display.<id>.name|icon} in TeaCore's config wins, then the game's
 * own translation and icon, then the capitalised id and paper.
 */
public final class GameDisplay {

    private final Plugin plugin;
    private final TeaCoreAPI core;

    public GameDisplay(Plugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    /** Legacy-coloured display name in {@code locale}. */
    public String name(String locale, GameId game) {
        MessageService ms = core.messageService();
        ConfigurationSection display = plugin.getConfig().getConfigurationSection("game-display." + game.value());
        if (display != null && display.isString("name")) {
            return ms.renderInline(display.getString("name"), Map.of());
        }
        GameDefinition def = core.games().get(game).orElse(null);
        if (def != null && ms.hasKey(def.displayNameKey())) {
            return ms.renderLegacyNoPrefix(locale, def.displayNameKey(), Map.of());
        }
        String id = game.value();
        return id.isEmpty() ? id : id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }

    /** Same as {@link #name} without colour codes. */
    public String plainName(String locale, GameId game) {
        return name(locale, game).replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    public Material icon(GameId game) {
        ConfigurationSection display = plugin.getConfig().getConfigurationSection("game-display." + game.value());
        if (display != null && display.isString("icon")) {
            Material m = Material.matchMaterial(display.getString("icon"));
            if (m != null) {
                return m;
            }
        }
        return core.games().get(game).map(GameDefinition::icon).orElse(Material.PAPER);
    }
}
