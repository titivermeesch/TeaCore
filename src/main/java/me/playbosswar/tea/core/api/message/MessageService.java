package me.playbosswar.tea.core.api.message;

import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Renders and dispatches localized, themed messages.
 *
 * <p>
 * Game plugins should never construct Adventure Components directly; instead
 * they
 * reference translation keys defined in each plugin's
 * {@code lang/<locale>.yml} bundle and the service resolves, themes, and
 * delivers the
 * text. Theme style slots wrapped in MiniMessage tags are resolved through the
 * active
 * {@link me.playbosswar.tea.core.api.theme.Theme}.
 */
public interface MessageService {

    /**
     * Sends a localized, themed message to a player.
     *
     * <p>
     * Looked up by {@code key} in the player's preferred locale, falls back to the
     * default locale if no translation exists. The theme prefix is applied.
     *
     * @param player       recipient; must not be {@code null}.
     * @param key          translation key; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     */
    void send(Player player, String key, Map<String, String> placeholders);

    /**
     * Sends a localized, themed message with no placeholders.
     *
     * <p>
     * Equivalent to {@link #send(Player, String, Map) send(player, key, Map.of())}.
     *
     * @param player recipient; must not be {@code null}.
     * @param key    translation key; must not be {@code null}.
     */
    default void send(Player player, String key) {
        send(player, key, Map.of());
    }

    /**
     * Sends a localized, themed message to any command sender (console, command
     * block, or
     * player), falling back to the default locale for non-player senders.
     *
     * @param sender       recipient; must not be {@code null}.
     * @param key          translation key; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     */
    void sendToSender(CommandSender sender, String key, Map<String, String> placeholders);

    /**
     * Sends a localized, themed message with no placeholders to any command sender.
     *
     * <p>
     * Equivalent to
     * {@link #sendToSender(CommandSender, String, Map) sendToSender(sender, key,
     * Map.of())}.
     *
     * @param sender recipient; must not be {@code null}.
     * @param key    translation key; must not be {@code null}.
     */
    default void sendToSender(CommandSender sender, String key) {
        sendToSender(sender, key, Map.of());
    }

    /**
     * Sends a localized, themed message as an action-bar overlay.
     *
     * @param player       recipient; must not be {@code null}.
     * @param key          translation key; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     */
    void sendActionBar(Player player, String key, Map<String, String> placeholders);

    /**
     * Sends a localized action-bar overlay with no placeholders.
     *
     * <p>
     * Equivalent to
     * {@link #sendActionBar(Player, String, Map) sendActionBar(player, key,
     * Map.of())}.
     *
     * @param player recipient; must not be {@code null}.
     * @param key    translation key; must not be {@code null}.
     */
    default void sendActionBar(Player player, String key) {
        sendActionBar(player, key, Map.of());
    }

    /**
     * Shows a localized, themed title overlay to a player via Adventure, with no
     * chat prefix applied.
     *
     * <p>
     * The same {@code placeholders} map is applied to both the title and the
     * subtitle lookup; MiniMessage simply ignores placeholder tags that are not
     * present in a given template, so one map can carry fields for either line.
     * Pass an empty string as {@code subtitleKey} to render only the main line.
     *
     * @param player        recipient; must not be {@code null}.
     * @param titleKey      translation key for the title; must not be
     *                      {@code null}.
     * @param subtitleKey   translation key for the subtitle, or {@code ""} for
     *                      none; must not be {@code null}.
     * @param placeholders  placeholder values substituted into both lines; never
     *                      {@code null}.
     * @param fadeInTicks   fade-in duration in game ticks.
     * @param stayTicks     on-screen duration in game ticks.
     * @param fadeOutTicks  fade-out duration in game ticks.
     */
    void showTitle(Player player, String titleKey, String subtitleKey,
            Map<String, String> placeholders, int fadeInTicks, int stayTicks, int fadeOutTicks);

    /**
     * Convenience overload for titles without placeholders.
     *
     * @param player        recipient; must not be {@code null}.
     * @param titleKey      title translation key; must not be {@code null}.
     * @param subtitleKey   subtitle translation key, or {@code ""} for none;
     *                      must not be {@code null}.
     * @param fadeInTicks   fade-in duration in ticks.
     * @param stayTicks     on-screen duration in ticks.
     * @param fadeOutTicks  fade-out duration in ticks.
     */
    default void showTitle(Player player, String titleKey, String subtitleKey,
            int fadeInTicks, int stayTicks, int fadeOutTicks) {
        showTitle(player, titleKey, subtitleKey, Map.of(),
                fadeInTicks, stayTicks, fadeOutTicks);
    }

    /**
     * Sends a raw MiniMessage string (with theme tags resolved) to the player.
     *
     * <p>
     * No translation lookup happens; useful for literal strings that are not worth
     * putting in a bundle.
     *
     * @param player      recipient; must not be {@code null}.
     * @param miniMessage MiniMessage input to render; must not be {@code null}.
     */
    void sendRaw(Player player, String miniMessage);

    /**
     * Sends a raw MiniMessage string with placeholder substitution.
     *
     * @param player       recipient; must not be {@code null}.
     * @param miniMessage  MiniMessage input with {@code <placeholder>} tags; must
     *                     not be
     *                     {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     */
    void sendRaw(Player player, String miniMessage, Map<String, String> placeholders);

    /**
     * Renders a translation key in the given locale, stripped to legacy
     * section-code text.
     *
     * <p>
     * Suitable for contexts that only accept legacy-coded strings (e.g. sign
     * lines).
     * The theme prefix is applied.
     *
     * @param locale       locale code (e.g. {@code "en_US"}) to render in; must not
     *                     be
     *                     {@code null}.
     * @param key          translation key; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     * @return the rendered legacy string, never {@code null}.
     */
    String renderLegacy(String locale, String key, Map<String, String> placeholders);

    /**
     * Renders a translation key to legacy section-code text without applying the
     * theme prefix.
     *
     * <p>
     * Use for contexts where the prefix is inappropriate: item display names,
     * scoreboard
     * lines, titles, GUI labels.
     *
     * @param locale       locale code to render in; must not be {@code null}.
     * @param key          translation key; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     * @return the rendered legacy string, never {@code null}.
     */
    String renderLegacyNoPrefix(String locale, String key, Map<String, String> placeholders);

    /**
     * Renders an inline MiniMessage string (not a translation key) to legacy
     * section-code text.
     *
     * <p>
     * Theme styles are resolved; the theme prefix is NOT applied. Use for
     * admin-authored
     * template strings coming from config files (e.g. scoreboard line templates).
     *
     * @param miniMessageInput MiniMessage template to render; must not be
     *                         {@code null}.
     * @param placeholders     placeholder values substituted into the message;
     *                         never
     *                         {@code null}.
     * @return the rendered legacy string, never {@code null}.
     */
    String renderInline(String miniMessageInput, Map<String, String> placeholders);

    /**
     * Renders a translation key in the given locale, stripped of all formatting.
     *
     * @param locale       locale code to render in; must not be {@code null}.
     * @param key          translation key; must not be {@code null}.
     * @param placeholders placeholder values substituted into the message; never
     *                     {@code null}.
     * @return the rendered plain-text string, never {@code null}.
     */
    String renderPlainText(String locale, String key, Map<String, String> placeholders);

    /**
     * Returns the locale currently resolved for a player.
     *
     * @param player player to inspect; must not be {@code null}.
     * @return the locale code (e.g. {@code "en_US"}), never {@code null}.
     */
    String resolveLocale(Player player);

    /**
     * Registers a plugin's {@code lang/<locale>.yml} bundles into the shared
     * message registry.
     *
     * <p>
     * Bundles are loaded from the plugin's jar and merged with an admin-customized
     * version at {@code plugins/<Plugin>/lang/} when present. Entries are merged
     * into any
     * existing locales; key collisions favor the newest registration, so dependent
     * plugins
     * should prefix their keys with a unique namespace.
     *
     * @param plugin  plugin whose bundles to load; must not be {@code null}.
     * @param locales list of locale codes whose files should be loaded (e.g.
     *                {@code ["en_US", "fr_FR"]}); must not be {@code null}.
     */
    void registerBundles(org.bukkit.plugin.Plugin plugin, java.util.List<String> locales);
}
