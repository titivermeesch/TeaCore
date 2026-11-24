package me.playbosswar.tea.core.api.message;

import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Renders and dispatches localized, themed messages.
 *
 * <p>
 * Plugins reference translation keys from their {@code lang/<locale>.yml}
 * bundles instead of building Adventure Components. Theme style tags are
 * resolved through the active {@link me.playbosswar.tea.core.api.theme.Theme}.
 */
public interface MessageService {

    /**
     * A key starting with this is not looked up: the rest is the MiniMessage text
     * itself, the same in every language. Use it for names staff type in, such
     * as {@code literal:<gold>Archer} for a kit added from the settings menu.
     */
    String LITERAL_PREFIX = "literal:";


    /**
     * Sends a message in the player's locale, falling back to the default locale.
     * The theme prefix is applied.
     */
    void send(Player player, String key, Map<String, String> placeholders);

    default void send(Player player, String key) {
        send(player, key, Map.of());
    }

    /** Non-player senders get the default locale. */
    void sendToSender(CommandSender sender, String key, Map<String, String> placeholders);

    default void sendToSender(CommandSender sender, String key) {
        sendToSender(sender, key, Map.of());
    }

    void sendActionBar(Player player, String key, Map<String, String> placeholders);

    default void sendActionBar(Player player, String key) {
        sendActionBar(player, key, Map.of());
    }

    /**
     * Shows a title without the chat prefix. {@code placeholders} applies to both
     * lines. Pass {@code ""} as {@code subtitleKey} for no subtitle. Durations are
     * in ticks.
     */
    void showTitle(Player player, String titleKey, String subtitleKey,
            Map<String, String> placeholders, int fadeInTicks, int stayTicks, int fadeOutTicks);

    default void showTitle(Player player, String titleKey, String subtitleKey,
            int fadeInTicks, int stayTicks, int fadeOutTicks) {
        showTitle(player, titleKey, subtitleKey, Map.of(),
                fadeInTicks, stayTicks, fadeOutTicks);
    }

    /** Sends a literal MiniMessage string with theme tags resolved; no translation lookup. */
    void sendRaw(Player player, String miniMessage);

    void sendRaw(Player player, String miniMessage, Map<String, String> placeholders);

    /** Renders to legacy section-code text, with the theme prefix. */
    String renderLegacy(String locale, String key, Map<String, String> placeholders);

    /**
     * Renders to legacy section-code text without the theme prefix. Use for item
     * names, scoreboard lines, titles and GUI labels.
     */
    String renderLegacyNoPrefix(String locale, String key, Map<String, String> placeholders);

    /**
     * Renders an inline MiniMessage template (not a translation key) to legacy
     * text. Theme styles are resolved; the theme prefix is not applied.
     */
    String renderInline(String miniMessageInput, Map<String, String> placeholders);

    /** Renders with all formatting stripped. */
    String renderPlainText(String locale, String key, Map<String, String> placeholders);

    /**
     * Renders to legacy lines, splitting on newlines or YAML list elements.
     * Never returns an empty list: an empty entry yields one empty line, so the
     * result can go straight to {@link org.bukkit.inventory.meta.ItemMeta#setLore}.
     */
    java.util.List<String> renderLegacyLines(String locale, String key, Map<String, String> placeholders);

    /** @return a locale code such as {@code "en_US"} */
    String resolveLocale(Player player);

    /**
     * The server's default locale ({@code i18n.default-locale}), for text shared by
     * every viewer or rendered without a player.
     */
    String defaultLocale();

    /**
     * Whether any registered bundle defines the key. Lets callers fall back to a
     * literal label when another game's bundle is not loaded on this server.
     */
    boolean hasKey(String key);

    /**
     * Loads a plugin's bundled {@code lang/<locale>.yml} files, merged with admin
     * overrides in {@code plugins/<Plugin>/lang/}. On key collisions the newest
     * registration wins, so prefix keys with a unique namespace.
     */
    void registerBundles(org.bukkit.plugin.Plugin plugin, java.util.List<String> locales);
}
