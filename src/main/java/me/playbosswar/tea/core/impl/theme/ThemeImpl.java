package me.playbosswar.tea.core.impl.theme;

import java.util.Map;
import java.util.Set;
import me.playbosswar.tea.core.api.theme.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.StyleBuilderApplicable;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class ThemeImpl implements Theme {

    private final Map<String, String> styles;
    private final String prefix;
    private final TagResolver resolver;

    public ThemeImpl(Map<String, String> styles, String prefix) {
        this.styles = Map.copyOf(styles);
        this.prefix = prefix;
        this.resolver = buildResolver(this.styles);
    }

    @Override
    public String tag(String slotName) {
        return styles.getOrDefault(slotName, "");
    }

    @Override
    public String prefix() {
        return prefix;
    }

    @Override
    public Set<String> slotNames() {
        return styles.keySet();
    }

    public TagResolver resolver() {
        return resolver;
    }

    private static TagResolver buildResolver(Map<String, String> styles) {
        TagResolver.Builder builder = TagResolver.builder();
        MiniMessage mm = MiniMessage.miniMessage();
        for (Map.Entry<String, String> entry : styles.entrySet()) {
            Style style = extractStyle(mm, entry.getValue());
            StyleBuilderApplicable applicable = sb -> sb.merge(style, Style.Merge.Strategy.ALWAYS);
            builder.resolver(TagResolver.resolver(entry.getKey(), Tag.styling(applicable)));
        }
        return builder.build();
    }

    /**
     * Parse a MiniMessage template like {@code <#5fd1ff>} or {@code <bold><white>} and
     * collapse its applied styles into a single {@link Style} that can back a styling tag.
     * Registering the style slot as a styling tag (rather than a parsed placeholder) is what
     * lets {@code <primary>text</primary>} render correctly, with the close tag
     * popping back to the parent style instead of leaking as literal text.
     */
    private static Style extractStyle(MiniMessage mm, String template) {
        Component root = mm.deserialize(template + "x");
        Style.Builder builder = Style.style();
        mergeStyle(root, builder);
        return builder.build();
    }

    private static void mergeStyle(Component component, Style.Builder target) {
        Style style = component.style();
        if (style.color() != null) target.color(style.color());
        if (style.font() != null) target.font(style.font());
        for (TextDecoration deco : TextDecoration.values()) {
            TextDecoration.State state = style.decoration(deco);
            if (state != TextDecoration.State.NOT_SET) {
                target.decoration(deco, state);
            }
        }
        for (Component child : component.children()) {
            mergeStyle(child, target);
        }
    }
}
