package me.playbosswar.tea.core.impl.message;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.theme.ThemeImpl;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageServiceImpl implements MessageService, AutoCloseable {

    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    private final Map<String, LocaleBundle> bundles;
    private final String defaultLocale;
    private final List<String> fallbackChain;
    private volatile ThemeImpl theme;

    private final Map<String, String> localeResolutionCache = new ConcurrentHashMap<>();
    /** Replayed on {@link #replaceBundles} so {@code /tea reload} keeps game-plugin translations. */
    private final Map<org.bukkit.plugin.Plugin, List<String>> registeredSources = new ConcurrentHashMap<>();

    public MessageServiceImpl(
            BukkitAudiences audiences,
            Map<String, LocaleBundle> bundles,
            String defaultLocale,
            List<String> fallbackChain,
            ThemeImpl theme) {
        this.audiences = audiences;
        this.bundles = new ConcurrentHashMap<>(bundles);
        this.defaultLocale = defaultLocale;
        this.fallbackChain = List.copyOf(fallbackChain);
        this.theme = theme;
    }

    public void replaceBundles(Map<String, LocaleBundle> bundles) {
        this.bundles.clear();
        this.bundles.putAll(bundles);
        this.localeResolutionCache.clear();
        for (Map.Entry<org.bukkit.plugin.Plugin, List<String>> entry : registeredSources.entrySet()) {
            mergePluginBundles(entry.getKey(), entry.getValue());
        }
    }

    public void replaceTheme(ThemeImpl theme) {
        this.theme = theme;
        inlineCache.invalidateAll();
    }

    @Override
    public void send(Player player, String key, Map<String, String> placeholders) {
        Audience aud = audiences.player(player);
        String locale = resolveLocale(player);
        aud.sendMessage(render(locale, key, placeholders));
    }

    @Override
    public void sendToSender(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player p) {
            send(p, key, placeholders);
            return;
        }
        Audience aud = audiences.sender(sender);
        aud.sendMessage(render(defaultLocale, key, placeholders));
    }

    @Override
    public void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        Audience aud = audiences.player(player);
        String locale = resolveLocale(player);
        aud.sendActionBar(render(locale, key, placeholders));
    }

    @Override
    public void showTitle(Player player, String titleKey, String subtitleKey,
            Map<String, String> placeholders, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        String locale = resolveLocale(player);
        Component titleComponent = renderRaw(lookup(locale, titleKey), placeholders);
        Component subtitleComponent = subtitleKey.isEmpty()
                ? Component.empty()
                : renderRaw(lookup(locale, subtitleKey), placeholders);
        Title title = Title.title(titleComponent, subtitleComponent,
                Title.Times.times(
                        Duration.ofMillis(fadeInTicks * 50L),
                        Duration.ofMillis(stayTicks * 50L),
                        Duration.ofMillis(fadeOutTicks * 50L)));
        audiences.player(player).showTitle(title);
    }

    @Override
    public void sendRaw(Player player, String miniMessageString) {
        Audience aud = audiences.player(player);
        aud.sendMessage(renderRaw(miniMessageString, Map.of()));
    }

    @Override
    public void sendRaw(Player player, String miniMessageString, Map<String, String> placeholders) {
        Audience aud = audiences.player(player);
        aud.sendMessage(renderRaw(miniMessageString, placeholders));
    }

    @Override
    public void registerBundles(org.bukkit.plugin.Plugin plugin, java.util.List<String> locales) {
        registeredSources.put(plugin, List.copyOf(locales));
        mergePluginBundles(plugin, locales);
        localeResolutionCache.clear();
    }

    private void mergePluginBundles(org.bukkit.plugin.Plugin plugin, java.util.List<String> locales) {
        for (String locale : locales) {
            LocaleBundle incoming = LocaleBundleLoader.load(plugin, plugin.getDataFolder().toPath(), locale);
            LocaleBundle existing = bundles.get(locale);
            java.util.Map<String, String> merged = new java.util.HashMap<>();
            if (existing != null)
                merged.putAll(existing.entries());
            merged.putAll(incoming.entries());
            bundles.put(locale, new LocaleBundle(locale, merged));
        }
    }

    @Override
    public String renderLegacy(String locale, String key, Map<String, String> placeholders) {
        Component c = render(locale, key, placeholders);
        return legacySerializer.serialize(c);
    }

    @Override
    public String renderLegacyNoPrefix(String locale, String key, Map<String, String> placeholders) {
        String template = lookup(locale, key);
        return legacySerializer.serialize(renderRaw(template, placeholders));
    }

    @Override
    public String renderInline(String miniMessageInput, Map<String, String> placeholders) {
        // Sidebars and menus render the same lines with the same values over and
        // over; parsing MiniMessage was their biggest cost. Output depends only on
        // the input, the values and the theme, so cache it (cleared on reload).
        String key = inlineKey(miniMessageInput, placeholders);
        return inlineCache.get(key, k -> legacySerializer.serialize(renderRaw(miniMessageInput, placeholders)));
    }

    private final com.github.benmanes.caffeine.cache.Cache<String, String> inlineCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(20_000)
                    .expireAfterAccess(java.time.Duration.ofMinutes(2))
                    .build();

    private static String inlineKey(String input, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length() + placeholders.size() * 16).append(input);
        placeholders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append('\u0000').append(e.getKey()).append('\u0001').append(e.getValue()));
        return sb.toString();
    }

    @Override
    public String renderPlainText(String locale, String key, Map<String, String> placeholders) {
        return renderLegacyNoPrefix(locale, key, placeholders)
                .replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
    }

    @Override
    public List<String> renderLegacyLines(String locale, String key, Map<String, String> placeholders) {
        String template = lookup(locale, key);
        // Split on real newlines (YAML lists / double-quoted strings) or on the
        // literal two-char "\n" sequence (single-quoted / plain YAML strings).
        // Each line is rendered separately so MiniMessage tag scopes don't bleed.
        String[] parts = template.split("\\\\n|\\r?\\n", -1);
        List<String> out = new java.util.ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(legacySerializer.serialize(renderRaw(part, placeholders)));
        }
        return out;
    }

    @Override
    public String resolveLocale(Player player) {
        String raw = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().locale(player);
        return localeResolutionCache.computeIfAbsent(raw, this::pickBundleLocale);
    }

    @Override
    public String defaultLocale() {
        return defaultLocale;
    }

    @Override
    public boolean hasKey(String key) {
        if (key.startsWith(LITERAL)) {
            return true;
        }
        for (LocaleBundle bundle : bundles.values()) {
            if (bundle.get(key) != null) return true;
        }
        return false;
    }

    private String pickBundleLocale(String clientLocale) {
        String normalized = clientLocale == null ? "" : clientLocale.replace('-', '_');
        if (bundles.containsKey(normalized))
            return normalized;

        String[] parts = normalized.split("_");
        if (parts.length > 0) {
            for (String candidate : bundles.keySet()) {
                if (candidate.toLowerCase(Locale.ROOT).startsWith(parts[0].toLowerCase(Locale.ROOT) + "_")) {
                    return candidate;
                }
            }
        }
        for (String fb : fallbackChain) {
            if (bundles.containsKey(fb))
                return fb;
        }
        return defaultLocale;
    }

    private Component render(String locale, String key, Map<String, String> placeholders) {
        String template = lookup(locale, key);
        return renderRaw(theme.prefix() + template, placeholders);
    }

    private Component renderRaw(String miniMessageString, Map<String, String> placeholders) {
        TagResolver.Builder b = TagResolver.builder().resolver(theme.resolver());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            b.resolver(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        return miniMessage.deserialize(miniMessageString, b.build());
    }

    private String lookup(String locale, String key) {
        if (key.startsWith(LITERAL)) {
            return key.substring(LITERAL.length());
        }
        LocaleBundle bundle = bundles.get(locale);
        if (bundle != null) {
            String v = bundle.get(key);
            if (v != null)
                return v;
        }
        for (String fb : fallbackChain) {
            LocaleBundle b = bundles.get(fb);
            if (b != null) {
                String v = b.get(key);
                if (v != null)
                    return v;
            }
        }
        return "<error>missing translation: " + key + "</error>";
    }

    private static final String LITERAL = me.playbosswar.tea.core.api.message.MessageService.LITERAL_PREFIX;

    @Override
    public void close() {
        audiences.close();
    }

    public Map<String, LocaleBundle> bundles() {
        return Map.copyOf(bundles);
    }
}
