package me.playbosswar.tea.core.impl.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.playbosswar.tea.core.api.menu.MenuClick;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Apply;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Field;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Kind;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Resolved;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Type;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.Change;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.FileView;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * The settings part of {@code /tea admin}: files, categories, fields, and the
 * entries of map and list settings. Values that can't be clicked into place are
 * typed in chat.
 */
public final class SettingsMenus implements Listener {

    public static final String PERMISSION = "tea.settings";
    private static final int WRAP = 38;

    private final TeaCorePlugin plugin;
    private final SettingsServiceImpl settings;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();
    private MenuService menus;

    public SettingsMenus(TeaCorePlugin plugin, SettingsServiceImpl settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void register(MenuService menus) {
        this.menus = menus;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        menus.registerSource("settings-files", (viewer, args) -> viewer.hasPermission(PERMISSION) ? files() : List.of());
        menus.registerSource("settings-categories",
                (viewer, args) -> viewer.hasPermission(PERMISSION) ? categories(args) : List.of());
        menus.registerSource("settings-fields",
                (viewer, args) -> viewer.hasPermission(PERMISSION) ? fields(args) : List.of());
        menus.registerSource("settings-entries",
                (viewer, args) -> viewer.hasPermission(PERMISSION) ? entries(args) : List.of());
        menus.registerAction("settings-field", this::onField);
        menus.registerAction("settings-entry", this::onEntry);
        menus.registerAction("settings-delete", this::onDelete);
        menus.registerAction("settings-up", this::onUp);
    }

    // ------------------------------------------------------------------ sources

    private List<MenuEntry> files() {
        List<MenuEntry> out = new ArrayList<>();
        for (FileView v : settings.views().values()) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("file", v.schema().id());
            values.put("title", v.schema().title());
            values.put("hosts", hosts(v));
            values.put("note", v.divergent().isEmpty() ? "" : "Some values differ between servers");
            out.add(new MenuEntry(values, icon(v.schema().icon(), "COMPARATOR")));
        }
        return out;
    }

    private List<MenuEntry> categories(Map<String, String> args) {
        FileView v = settings.view(args.getOrDefault("file", ""));
        if (v == null) {
            return List.of();
        }
        List<MenuEntry> out = new ArrayList<>();
        for (SettingsSchema.Category c : v.schema().categories()) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("file", v.schema().id());
            values.put("category", c.id());
            values.put("title", c.title());
            values.put("count", String.valueOf(c.fields().size()));
            out.add(new MenuEntry(values, icon(c.icon(), "PAPER")));
        }
        return out;
    }

    private List<MenuEntry> fields(Map<String, String> args) {
        FileView v = settings.view(args.getOrDefault("file", ""));
        if (v == null) {
            return List.of();
        }
        String base = args.getOrDefault("base", "");
        List<Field> fields;
        String prefix;
        if (base.isEmpty()) {
            fields = v.schema().category(args.getOrDefault("category", "")).map(SettingsSchema.Category::fields)
                    .orElse(List.of());
            prefix = "";
        } else {
            Resolved r = v.schema().resolve(base).orElse(null);
            if (r == null || r.kind() == Kind.VALUE) {
                return List.of();
            }
            fields = r.field().fields();
            prefix = base + ".";
        }
        List<MenuEntry> out = new ArrayList<>();
        for (Field f : fields) {
            String path = prefix + f.path();
            Object value = v.value(path);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("file", v.schema().id());
            values.put("path", path);
            values.put("title", f.title());
            values.put("value", display(f, value));
            List<String> info = new ArrayList<>(wrap(f.help()));
            info.add(applyNote(f.apply()));
            if (base.isEmpty() && v.divergent().contains(f.path())) {
                info.add("Differs between servers");
            }
            values.put("info", String.join("\n", info));
            values.put("controls", String.join("\n", controls(f)));
            out.add(new MenuEntry(values, fieldIcon(f, value)));
        }
        return out;
    }

    private List<MenuEntry> entries(Map<String, String> args) {
        FileView v = settings.view(args.getOrDefault("file", ""));
        String path = args.getOrDefault("path", "");
        Field field = v == null ? null : v.schema().resolve(path).map(Resolved::field).orElse(null);
        if (field == null || !field.isContainer()) {
            return List.of();
        }
        List<MenuEntry> out = new ArrayList<>();
        Object value = v.value(path);
        if (field.type() == Type.MAP && value instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.add(entry(v, field, path + "." + e.getKey(), String.valueOf(e.getKey()), e.getValue()));
            }
        } else if (field.type() == Type.LIST && value instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                Object item = l.get(i);
                out.add(entry(v, field, path + "[" + i + "]", itemTitle(item, i), item));
            }
        }
        Map<String, String> add = new LinkedHashMap<>();
        add.put("file", v.schema().id());
        add.put("path", path);
        add.put("kind", "add");
        add.put("title", field.type() == Type.MAP ? "Add an entry" : "Add an item");
        add.put("info", field.type() == Type.MAP ? "You type its id in chat" : "Added at the end");
        add.put("controls", "Click to add");
        out.add(new MenuEntry(add, icon("LIME_DYE", "EMERALD")));
        if (field.fromInventory()) {
            Map<String, String> inv = new LinkedHashMap<>();
            inv.put("file", v.schema().id());
            inv.put("path", path);
            inv.put("kind", "inventory");
            inv.put("title", "Copy from my inventory");
            inv.put("info", "Replaces the list with the items you carry,\nin the slots you carry them in");
            inv.put("controls", "Shift-click to replace");
            out.add(new MenuEntry(inv, icon("CHEST", "CHEST")));
        }
        return out;
    }

    private MenuEntry entry(FileView v, Field container, String path, String title, Object value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("file", v.schema().id());
        values.put("path", path);
        values.put("kind", "entry");
        values.put("title", title);
        List<String> info = new ArrayList<>();
        for (Field c : container.fields()) {
            Object cv = SettingsValues.get(value, c.path());
            if (cv != null && !c.isContainer() && info.size() < 6) {
                info.add(c.title() + ": " + display(c, cv));
            }
        }
        values.put("info", String.join("\n", info));
        values.put("controls", "Click to edit\nShift-right-click to remove");
        Object material = null;
        for (Field c : container.fields()) {
            if (c.type() == Type.MATERIAL && material == null) {
                material = SettingsValues.get(value, c.path());
            }
        }
        return new MenuEntry(values, icon(material == null ? null : String.valueOf(material), "PAPER"));
    }

    // ------------------------------------------------------------------ actions

    private void onField(MenuClick click, String arg) {
        Player p = click.player();
        if (!allowed(p)) {
            return;
        }
        String[] parts = arg.split("\\|", 2);
        if (parts.length < 2) {
            return;
        }
        String file = parts[0];
        String path = parts[1];
        FileView v = settings.view(file);
        Field f = v == null ? null : v.schema().resolve(path).map(Resolved::field).orElse(null);
        if (f == null) {
            return;
        }
        Object current = v.value(path);
        ClickType type = click.type();
        switch (f.type()) {
            case BOOLEAN -> save(click, file, path, !Boolean.TRUE.equals(current), f);
            case INT, NUMBER -> {
                if (type == ClickType.SHIFT_RIGHT) {
                    prompt(click, f, text -> save(click, file, path, text.trim(), f));
                    return;
                }
                double now = current instanceof Number n ? n.doubleValue() : 0;
                double delta = f.step() * (type == ClickType.SHIFT_LEFT ? 10 : 1) * (type == ClickType.RIGHT ? -1 : 1);
                double next = now + delta;
                if (f.min() != null) next = Math.max(f.min(), next);
                if (f.max() != null) next = Math.min(f.max(), next);
                Object out = f.type() == Type.INT ? (Object) Math.round(next)
                        : (Object) (Math.round(next * 1000.0) / 1000.0);
                save(click, file, path, out, f);
            }
            case ENUM -> {
                if (f.options().isEmpty()) return;
                int i = f.options().indexOf(String.valueOf(current));
                int n = f.options().size();
                int next = type.isRightClick() ? (i <= 0 ? n - 1 : i - 1) : (i + 1) % n;
                save(click, file, path, f.options().get(next), f);
            }
            case MATERIAL -> {
                if (type.isRightClick()) {
                    prompt(click, f, text -> save(click, file, path, text, f));
                    return;
                }
                String held = plugin.platform().items().nameOf(p.getItemInHand());
                if (held == null || held.equals("AIR")) {
                    send(p, "core.settings.hold-item", Map.of());
                    return;
                }
                save(click, file, path, held, f);
            }
            case TEXT -> prompt(click, f, text -> save(click, file, path, text, f));
            case TEXT_LIST -> {
                List<String> lines = new ArrayList<>();
                if (current instanceof List<?> l) {
                    for (Object o : l) lines.add(String.valueOf(o));
                }
                if (type.isRightClick()) {
                    if (!lines.isEmpty()) {
                        lines.remove(lines.size() - 1);
                        save(click, file, path, lines, f);
                    }
                    return;
                }
                prompt(click, f, text -> {
                    lines.add(text);
                    save(click, file, path, lines, f);
                });
            }
            case MAP, LIST -> menus.open(p, "admin-settings-entries", Map.of("file", file, "path", path,
                    "category", click.args().getOrDefault("category", "")));
        }
    }

    private void onEntry(MenuClick click, String arg) {
        Player p = click.player();
        if (!allowed(p)) {
            return;
        }
        String[] parts = arg.split("\\|", 3);
        if (parts.length < 3) {
            return;
        }
        String file = parts[0];
        String path = parts[1];
        String kind = parts[2];
        FileView v = settings.view(file);
        if (v == null) {
            return;
        }
        switch (kind) {
            case "entry" -> {
                if (click.type() == ClickType.SHIFT_RIGHT) {
                    apply(p, file, List.of(new Change(path, null)), null, () -> reopen(click));
                    return;
                }
                openEntry(p, file, path, click.args());
            }
            case "add" -> {
                Field field = v.schema().resolve(path).map(Resolved::field).orElse(null);
                if (field == null) {
                    return;
                }
                Map<String, Object> fresh = new LinkedHashMap<>(field.newEntry());
                if (field.type() == Type.LIST) {
                    Object cur = v.value(path);
                    int index = cur instanceof List<?> l ? l.size() : 0;
                    if (fresh.isEmpty() && field.child("material").isPresent()) {
                        fresh.put("material", "STONE");
                    }
                    String itemPath = path + "[" + index + "]";
                    apply(p, file, List.of(new Change(itemPath, fresh)), null,
                            () -> openEntry(p, file, itemPath, click.args()));
                    return;
                }
                p.closeInventory();
                ask(p, "core.settings.prompt-id", Map.of(), text -> {
                    String id = text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                    if (!SettingsSchema.validEntryId(id)) {
                        send(p, "core.settings.bad-id", Map.of());
                        return;
                    }
                    if (v.value(path + "." + id) != null) {
                        send(p, "core.settings.id-taken", Map.of("id", id));
                        return;
                    }
                    String entryPath = path + "." + id;
                    apply(p, file, List.of(new Change(entryPath, fresh)), null,
                            () -> openEntry(p, file, entryPath, click.args()));
                });
            }
            case "inventory" -> {
                if (click.type() != ClickType.SHIFT_LEFT && click.type() != ClickType.SHIFT_RIGHT) {
                    send(p, "core.settings.shift-to-copy", Map.of());
                    return;
                }
                List<Object> items = inventoryItems(p);
                apply(p, file, List.of(new Change(path, items)), null, () -> reopen(click));
            }
            default -> { }
        }
    }

    private void onDelete(MenuClick click, String arg) {
        Player p = click.player();
        String[] parts = arg.split("\\|", 2);
        if (!allowed(p) || parts.length < 2 || parts[1].isEmpty()) {
            return;
        }
        String path = parts[1];
        String category = click.args().getOrDefault("category", "");
        apply(p, parts[0], List.of(new Change(path, null)), null, () -> menus.open(p, "admin-settings-entries",
                Map.of("file", parts[0], "path", containerOf(path), "category", category)));
    }

    /** Back from an entry to its list, or from a list to the fields it sits between. */
    private void onUp(MenuClick click, String arg) {
        Player p = click.player();
        String[] parts = arg.split("\\|", 2);
        String file = parts[0];
        String path = parts.length > 1 ? parts[1] : "";
        String category = click.args().getOrDefault("category", "");
        FileView v = settings.view(file);
        Resolved r = v == null || path.isEmpty() ? null : v.schema().resolve(path).orElse(null);
        if (r == null) {
            menus.open(p, "admin-settings-file", Map.of("file", file));
            return;
        }
        if (r.kind() != Kind.VALUE) {
            menus.open(p, "admin-settings-entries", Map.of("file", file, "path", containerOf(path),
                    "category", category));
            return;
        }
        if (path.equals(r.top().path())) {
            menus.open(p, "admin-settings-fields", Map.of("file", file, "category", category));
            return;
        }
        String entry = path.substring(0, path.length() - r.field().path().length() - 1);
        openEntry(p, file, entry, click.args());
    }

    private void openEntry(Player p, String file, String path, Map<String, String> args) {
        menus.open(p, "admin-settings-entry", Map.of("file", file, "base", path,
                "category", args.getOrDefault("category", ""), "title", lastSegment(path)));
    }

    private static String containerOf(String path) {
        return path.endsWith("]") ? path.substring(0, path.lastIndexOf('['))
                : path.substring(0, path.lastIndexOf('.'));
    }

    private static String lastSegment(String path) {
        if (path.endsWith("]")) {
            return "Item " + (Integer.parseInt(path.substring(path.lastIndexOf('[') + 1, path.length() - 1)) + 1);
        }
        return path.substring(path.lastIndexOf('.') + 1);
    }

    // ----------------------------------------------------------------- applying

    private void save(MenuClick click, String file, String path, Object value, Field f) {
        apply(click.player(), file, List.of(new Change(path, value)), f, () -> reopen(click));
    }

    private void apply(Player p, String file, List<Change> changes, @Nullable Field f, Runnable after) {
        try {
            List<String> targets = settings.apply(file, changes, p.getName(), null);
            if (targets.isEmpty()) {
                after.run();
                return;
            }
            boolean remoteOnly = !targets.contains(plugin.coordination().serverId());
            String note = f == null ? "" : applyNote(f.apply());
            send(p, remoteOnly ? "core.settings.saved-remote" : "core.settings.saved",
                    Map.of("note", note, "servers", String.valueOf(targets.size())));
            // Other servers answer with fresh values a moment later.
            Bukkit.getScheduler().runTaskLater(plugin, after, remoteOnly ? 20L : 2L);
        } catch (IllegalArgumentException e) {
            send(p, "core.settings.invalid", Map.of("reason", e.getMessage()));
        }
    }

    private void reopen(MenuClick click) {
        if (click.player().isOnline()) {
            menus.open(click.player(), click.menuId(), click.args());
        }
    }

    // ------------------------------------------------------------------ prompts

    private void prompt(MenuClick click, Field f, Consumer<String> then) {
        click.player().closeInventory();
        ask(click.player(), "core.settings.prompt", Map.of("setting", f.title()), then);
    }

    private void ask(Player p, String key, Map<String, String> values, Consumer<String> then) {
        send(p, key, values);
        UUID id = p.getUniqueId();
        prompts.put(id, then);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (prompts.remove(id, then) && p.isOnline()) {
                send(p, "core.settings.prompt-expired", Map.of());
            }
        }, 20L * 60);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> then = prompts.remove(event.getPlayer().getUniqueId());
        if (then == null) {
            return;
        }
        event.setCancelled(true);
        String text = event.getMessage();
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.trim().equalsIgnoreCase("cancel")) {
                send(p, "core.settings.prompt-cancelled", Map.of());
                return;
            }
            then.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ helpers

    private List<Object> inventoryItems(Player p) {
        List<Object> items = new ArrayList<>();
        ItemStack[] contents = p.getInventory().getContents();
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int slot = 0; slot < 36 && slot < contents.length; slot++) {
            addItem(items, contents[slot], slot);
        }
        // Armor contents run boots, leggings, chestplate, helmet: slots 36 to 39.
        for (int i = 0; i < armor.length && i < 4; i++) {
            addItem(items, armor[i], 36 + i);
        }
        return items;
    }

    private void addItem(List<Object> items, @Nullable ItemStack stack, int slot) {
        String name = stack == null ? null : plugin.platform().items().nameOf(stack);
        if (name == null || name.equals("AIR")) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("material", name);
        if (stack.getAmount() > 1) {
            item.put("amount", stack.getAmount());
        }
        item.put("slot", slot);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            item.put("name", MiniMessage.miniMessage().serialize(
                    LegacyComponentSerializer.legacySection().deserialize(meta.getDisplayName())));
        }
        items.add(item);
    }

    private boolean allowed(Player p) {
        if (p.hasPermission(PERMISSION)) {
            return true;
        }
        send(p, "core.command.no-permission", Map.of());
        return false;
    }

    private void send(Player p, String key, Map<String, String> values) {
        plugin.messageService().send(p, key, values);
    }

    private String hosts(FileView v) {
        if (v.hosts().size() == 1) {
            return v.hosts().get(0).equals(plugin.coordination().serverId()) ? "this server" : v.hosts().get(0);
        }
        return v.hosts().size() + " servers";
    }

    static String applyNote(Apply apply) {
        return switch (apply) {
            case LIVE -> "Takes effect right away";
            case NEXT_MATCH -> "Takes effect from the next match";
            case RESTART -> "Takes effect after a restart";
        };
    }

    private static List<String> controls(Field f) {
        return switch (f.type()) {
            case BOOLEAN -> List.of("Click to switch");
            case INT, NUMBER -> List.of("Left: +" + SettingsSchema.plain(f.step()) + "  Right: -"
                    + SettingsSchema.plain(f.step()), "Shift-left: +" + SettingsSchema.plain(f.step() * 10)
                    + "  Shift-right: type a value");
            case ENUM -> List.of("Left: next  Right: previous");
            case MATERIAL -> List.of("Left: use the item in your hand", "Right: type a name");
            case TEXT -> List.of("Click to type a new value");
            case TEXT_LIST -> List.of("Left: add a line  Right: remove the last");
            case MAP, LIST -> List.of("Click to open");
        };
    }

    static String display(Field f, @Nullable Object value) {
        if (value == null) {
            return "not set";
        }
        if (f.secret()) {
            return String.valueOf(value).isEmpty() ? "not set" : "hidden";
        }
        return switch (f.type()) {
            case BOOLEAN -> Boolean.TRUE.equals(value) ? "on" : "off";
            case NUMBER, INT -> value instanceof Number n ? SettingsSchema.plain(n.doubleValue()) : String.valueOf(value);
            case TEXT_LIST -> value instanceof List<?> l ? l.size() + (l.size() == 1 ? " line" : " lines") : "";
            case MAP -> value instanceof Map<?, ?> m ? m.size() + (m.size() == 1 ? " entry" : " entries") : "";
            case LIST -> value instanceof List<?> l ? l.size() + (l.size() == 1 ? " item" : " items") : "";
            default -> {
                String s = String.valueOf(value);
                yield s.isEmpty() ? "empty" : s.length() > 40 ? s.substring(0, 37) + "..." : s;
            }
        };
    }

    private static String itemTitle(Object item, int index) {
        Object name = SettingsValues.get(item, "name");
        Object material = item instanceof String s ? s : SettingsValues.get(item, "material");
        if (material != null) {
            String m = String.valueOf(material).toLowerCase(Locale.ROOT).replace('_', ' ');
            return (index + 1) + ". " + (name != null ? stripTags(String.valueOf(name)) + " (" + m + ")" : m);
        }
        return "Item " + (index + 1);
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", "");
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > WRAP) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private ItemStack fieldIcon(Field f, @Nullable Object value) {
        if (f.icon() != null) {
            return icon(f.icon(), "PAPER");
        }
        return switch (f.type()) {
            case BOOLEAN -> Boolean.TRUE.equals(value) ? icon("LIME_DYE", "EMERALD") : icon("GRAY_DYE", "COAL");
            case INT, NUMBER -> icon("REPEATER", "REDSTONE");
            case TEXT -> icon("NAME_TAG", "PAPER");
            case ENUM -> icon("COMPARATOR", "REDSTONE");
            case MATERIAL -> icon(value == null ? null : String.valueOf(value), "STONE");
            case TEXT_LIST -> icon("WRITABLE_BOOK", "BOOK");
            case MAP, LIST -> icon("CHEST", "CHEST");
        };
    }

    private ItemStack icon(@Nullable String name, String fallback) {
        return plugin.platform().items().stackOr(name == null ? fallback : name, fallback, 1);
    }
}
