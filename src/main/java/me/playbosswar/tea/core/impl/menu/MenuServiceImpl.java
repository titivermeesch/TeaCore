package me.playbosswar.tea.core.impl.menu;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.feature.FeatureService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.menu.MenuAction;
import me.playbosswar.tea.core.api.menu.MenuClick;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.menu.MenuSource;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.menu.bedrock.BedrockBridge;
import me.playbosswar.tea.core.impl.sidebar.PapiBridge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class MenuServiceImpl implements MenuService, Listener {

    private final Plugin core;
    private final MessageService messages;
    private final FeatureService features;
    private final Logger logger;
    private final Map<String, LoadedMenu> menus = new ConcurrentHashMap<>();
    private final Map<String, MenuSource> sources = new ConcurrentHashMap<>();
    private final Map<String, MenuAction> actions = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Opened>> history = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private long ticks;
    /** Null without Floodgate. */
    private volatile BedrockBridge bedrock;
    private final Map<UUID, Holder> forms = new ConcurrentHashMap<>();

    private record LoadedMenu(String id, Plugin owner, File file, MenuDef def) { }

    private record Opened(String menuId, Map<String, String> args, int page) { }

    public MenuServiceImpl(Plugin core, MessageService messages, FeatureService features, Logger logger) {
        this.core = core;
        this.messages = messages;
        this.features = features;
        this.logger = logger;
        registerBuiltinActions();
    }

    public void setBedrock(BedrockBridge bedrock) {
        this.bedrock = bedrock;
    }

    public void start() {
        if (refreshTask == null) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(core, this::tickRefresh, 10L, 10L);
        }
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @Override
    public String registerMenu(Plugin owner, String resourcePath) {
        File file = new File(owner.getDataFolder(), resourcePath);
        if (!file.exists() && owner.getResource(resourcePath) != null) {
            owner.saveResource(resourcePath, false);
        }
        String base = file.getName().replaceFirst("\\.yml$", "");
        String id = owner == core ? base : owner.getName().toLowerCase(Locale.ROOT) + ":" + base;
        load(id, owner, file);
        return id;
    }

    public void loadCustomMenus() {
        File dir = new File(core.getDataFolder(), "menus");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().replaceFirst("\\.yml$", "");
            if (!menus.containsKey(id)) {
                load(id, core, file);
            }
        }
    }

    private void load(String id, Plugin owner, File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            menus.put(id, new LoadedMenu(id, owner, file, MenuDef.parse(yaml)));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not load menu " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void reload() {
        for (LoadedMenu menu : List.copyOf(menus.values())) {
            load(menu.id(), menu.owner(), menu.file());
        }
        loadCustomMenus();
    }

    @Override
    public void registerSource(String id, MenuSource source) {
        sources.put(id, source);
    }

    @Override
    public void registerAction(String id, MenuAction action) {
        actions.put(id, action);
    }

    /** Null when anyone may open it. */
    public String permissionOf(String menuId) {
        LoadedMenu menu = menus.get(menuId);
        return menu == null ? null : menu.def().permission();
    }

    @Override
    public Set<String> menuIds() {
        return Set.copyOf(menus.keySet());
    }

    @Override
    public boolean isEnabled(String menuId) {
        LoadedMenu menu = menus.get(menuId);
        if (menu == null || !menu.def().enabled()) {
            return false;
        }
        // A game's menus ("<game>:<file>") follow features.games.<game>.menus,
        // which falls back to the server-wide features.menus.
        int colon = menuId.indexOf(':');
        if (colon > 0) {
            try {
                return features.enabled(GameId.of(menuId.substring(0, colon)), "menus");
            } catch (IllegalArgumentException e) {
                return features.enabled("menus");
            }
        }
        return features.enabled("menus");
    }

    @Override
    public boolean open(Player player, String menuId, Map<String, String> args) {
        String permission = permissionOf(menuId);
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            return false;
        }
        return open(player, menuId, args == null ? Map.of() : args, 0, true);
    }

    /** Skips the menu's {@code permission:} check (an admin opening it for someone). */
    public boolean openBypassingPermission(Player player, String menuId, Map<String, String> args) {
        return open(player, menuId, args == null ? Map.of() : args, 0, true);
    }

    private boolean open(Player player, String menuId, Map<String, String> args, int page, boolean pushHistory) {
        if (!isEnabled(menuId)) {
            return false;
        }
        LoadedMenu menu = menus.get(menuId);
        Map<String, String> base = new HashMap<>(args);
        base.put("player", player.getName());
        Holder current = currentHolder(player);
        if (current == null && pushHistory) {
            // Opened from outside a menu (command, item): start a fresh history.
            history.remove(player.getUniqueId());
        } else if (pushHistory && current != null && !current.menuId.equals(menuId)) {
            history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>())
                    .push(new Opened(current.menuId, current.args, current.page));
        }
        Holder holder = new Holder(menuId, Map.copyOf(args), page);
        String title = text(player, menu.def().title(), base);
        Inventory inventory = Bukkit.createInventory(holder, menu.def().rows() * 9, title);
        holder.inventory = inventory;
        holder.title = title;
        render(player, menu.def(), holder, base);
        BedrockBridge bridge = bedrock;
        if (bridge != null && menu.def().bedrock() && bridge.isBedrock(player)) {
            holder.form = true;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                player.closeInventory();
            }
            sendForm(player, holder);
            return true;
        }
        forms.remove(player.getUniqueId());
        player.openInventory(inventory);
        return true;
    }

    private Holder currentHolder(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) {
            return h;
        }
        return forms.get(player.getUniqueId());
    }

    private static final ClickType[] FORM_CLICKS = {
        ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT};
    private static final String[] FORM_CLICK_KEYS = {"left", "right", "shift-left", "shift-right"};

    private void sendForm(Player player, Holder holder) {
        BedrockBridge bridge = bedrock;
        if (bridge == null) {
            return;
        }
        List<Integer> slots = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        for (int slot = 0; slot < holder.inventory.getSize(); slot++) {
            Click click = holder.clicks.get(slot);
            ItemStack stack = holder.inventory.getItem(slot);
            if (click == null || stack == null || !click.item().clickable()) {
                continue;
            }
            slots.add(slot);
            buttons.add(bedrockText(label(stack)));
        }
        holder.sends++;
        forms.put(player.getUniqueId(), holder);
        bridge.send(player, new BedrockBridge.Form(bedrockText(holder.title), "", buttons,
                pick -> {
                    if (forms.get(player.getUniqueId()) == holder && pick >= 0 && pick < slots.size()) {
                        pickSlot(player, holder, slots.get(pick));
                    }
                },
                () -> forms.remove(player.getUniqueId(), holder)));
    }

    /** Items that do different things per click type get a second form listing those clicks. */
    private void pickSlot(Player player, Holder holder, int slot) {
        Click click = holder.clicks.get(slot);
        if (click == null) {
            return;
        }
        List<ClickType> variants = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<List<String>> seen = new ArrayList<>();
        String locale = messages.resolveLocale(player);
        for (int i = 0; i < FORM_CLICKS.length; i++) {
            List<String> run = click.item().actionsFor(FORM_CLICKS[i]);
            if (!run.isEmpty() && !seen.contains(run)) {
                seen.add(run);
                variants.add(FORM_CLICKS[i]);
                labels.add(bedrockText(messages.renderLegacyNoPrefix(locale,
                        "core.menu.bedrock." + FORM_CLICK_KEYS[i], Map.of())));
            }
        }
        if (variants.size() <= 1) {
            runFormClick(player, holder, click, variants.isEmpty() ? ClickType.LEFT : variants.get(0));
            return;
        }
        ItemStack stack = holder.inventory.getItem(slot);
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        String content = meta != null && meta.getLore() != null ? String.join("\n", meta.getLore()) : "";
        labels.add(bedrockText(messages.renderLegacyNoPrefix(locale, "core.menu.bedrock.back", Map.of())));
        bedrock.send(player, new BedrockBridge.Form(bedrockText(stack == null ? holder.title : label(stack)),
                bedrockText(content), labels,
                pick -> {
                    if (forms.get(player.getUniqueId()) != holder) {
                        return;
                    }
                    if (pick >= 0 && pick < variants.size()) {
                        runFormClick(player, holder, click, variants.get(pick));
                    } else {
                        sendForm(player, holder);
                    }
                },
                () -> forms.remove(player.getUniqueId(), holder)));
    }

    /**
     * The form closes once a button is pressed, so it only comes back if an action
     * shows a menu again (refresh, paging, back, open-menu).
     */
    private void runFormClick(Player player, Holder holder, Click click, ClickType type) {
        int sends = holder.sends;
        MenuClick context = new MenuClick(player, holder.menuId, holder.args, click.values(), type, this);
        for (String raw : click.item().actionsFor(type)) {
            runAction(context, holder, fill(raw, click.values()));
        }
        if (holder.sends == sends) {
            forms.remove(player.getUniqueId(), holder);
        }
    }

    private static String label(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && !meta.getDisplayName().isBlank()) {
            return meta.getDisplayName();
        }
        String name = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Bedrock reads section codes too, but uses m and n as colours, not strikethrough/underline. */
    static String bedrockText(String legacy) {
        return legacy == null ? "" : legacy.replaceAll("(?i)\u00a7[mn]", "");
    }

    private void render(Player player, MenuDef def, Holder holder, Map<String, String> base) {
        Inventory inv = holder.inventory;
        inv.clear();
        holder.clicks.clear();
        Map<Character, List<Integer>> listCells = new LinkedHashMap<>();
        for (int slot = 0; slot < def.rows() * 9; slot++) {
            char c = def.charAt(slot);
            if (def.lists().containsKey(c)) {
                listCells.computeIfAbsent(c, k -> new ArrayList<>()).add(slot);
            }
        }
        // Resolve list entries first so paging values are known for fixed items.
        int pages = 1;
        Map<Character, List<MenuEntry>> entries = new HashMap<>();
        for (Map.Entry<Character, List<Integer>> cells : listCells.entrySet()) {
            MenuDef.ListDef list = def.lists().get(cells.getKey());
            MenuSource source = sources.get(list.source());
            List<MenuEntry> found = source == null ? List.of() : safeEntries(source, player, holder.args);
            entries.put(cells.getKey(), found);
            int perPage = Math.max(1, cells.getValue().size());
            pages = Math.max(pages, (found.size() + perPage - 1) / perPage);
        }
        holder.pages = Math.max(1, pages);
        holder.page = Math.max(0, Math.min(holder.page, holder.pages - 1));
        Map<String, String> pageValues = new HashMap<>(base);
        pageValues.put("page", String.valueOf(holder.page + 1));
        pageValues.put("pages", String.valueOf(holder.pages));

        for (int slot = 0; slot < def.rows() * 9; slot++) {
            MenuDef.ItemDef item = def.items().get(def.charAt(slot));
            if (item == null || !visible(player, item, pageValues, holder)) {
                continue;
            }
            inv.setItem(slot, build(player, item, pageValues, null));
            holder.clicks.put(slot, new Click(item, pageValues));
        }
        for (Map.Entry<Character, List<Integer>> cells : listCells.entrySet()) {
            MenuDef.ListDef list = def.lists().get(cells.getKey());
            List<MenuEntry> found = entries.get(cells.getKey());
            List<Integer> slots = cells.getValue();
            if (found.isEmpty() && list.empty() != null && !slots.isEmpty()) {
                inv.setItem(slots.get(0), build(player, list.empty(), pageValues, null));
                continue;
            }
            int offset = holder.page * slots.size();
            for (int i = 0; i < slots.size() && offset + i < found.size(); i++) {
                MenuEntry entry = found.get(offset + i);
                Map<String, String> values = new HashMap<>(pageValues);
                values.putAll(entry.values());
                inv.setItem(slots.get(i), build(player, list.item(), values, entry.icon()));
                holder.clicks.put(slots.get(i), new Click(list.item(), values));
            }
        }
    }

    private List<MenuEntry> safeEntries(MenuSource source, Player player, Map<String, String> args) {
        try {
            List<MenuEntry> entries = source.entries(player, args);
            return entries == null ? List.of() : entries;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Menu source failed", e);
            return List.of();
        }
    }

    /** Paging buttons hide themselves when there is nowhere to go. */
    private boolean visible(Player player, MenuDef.ItemDef item, Map<String, String> values, Holder holder) {
        List<String> all = new ArrayList<>(item.actions());
        if (all.contains("next-page") && holder.page >= holder.pages - 1) {
            return false;
        }
        if (all.contains("previous-page") && holder.page <= 0) {
            return false;
        }
        if (all.contains("back") && history.getOrDefault(player.getUniqueId(), new ArrayDeque<>()).isEmpty()) {
            return item.showWithoutHistory();
        }
        String viewIf = item.viewIf();
        return viewIf == null || truthy(fill(viewIf, values));
    }

    private ItemStack build(Player player, MenuDef.ItemDef def, Map<String, String> values, ItemStack icon) {
        ItemStack stack;
        if (icon != null) {
            stack = icon.clone();
        } else {
            // Modern item names (RED_WOOL) work on every version through the platform.
            stack = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items()
                    .stackOr(fill(def.material(), values).trim(), "STONE", 1);
        }
        String amount = def.amount() == null ? null : fill(def.amount(), values);
        if (amount != null) {
            try {
                stack.setAmount(Math.max(1, Math.min(64, Integer.parseInt(amount.trim()))));
            } catch (NumberFormatException ignored) {
                // keep the default amount
            }
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (def.name() != null) {
            meta.setDisplayName(text(player, def.name(), values));
        }
        if (!def.lore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) {
                // A line using a multi-line value (e.g. {kit_desc}) becomes one
                // lore line per value line.
                String multiKey = null;
                for (Map.Entry<String, String> e : values.entrySet()) {
                    if (e.getValue() != null && e.getValue().indexOf('\n') >= 0
                            && line.contains("{" + e.getKey() + "}")) {
                        multiKey = e.getKey();
                        break;
                    }
                }
                if (multiKey == null) {
                    lore.add(text(player, line, values));
                } else {
                    for (String part : values.get(multiKey).split("\n", -1)) {
                        Map<String, String> one = new HashMap<>(values);
                        one.put(multiKey, part);
                        lore.add(text(player, line, one));
                    }
                }
            }
            meta.setLore(lore);
        }
        if (def.glow() != null && truthy(fill(def.glow(), values))) {
            Enchantment glint = me.playbosswar.tea.core.impl.platform.Compat.glintEnchantment();
            if (glint != null) {
                meta.addEnchant(glint, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (def.customModelData() != null) {
            me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().setCustomModelData(meta, def.customModelData());
        }
        // Skins need Mojang's session servers, so heads only get an owner on an
        // online-mode server (offline-mode lookups fail and spam the console).
        if (def.skullOwner() != null && meta instanceof SkullMeta skull && Bukkit.getOnlineMode()) {
            skull.setOwner(fill(def.skullOwner(), values));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * {@code {key}} values are inserted as-is (never parsed), so legacy colour codes or
     * angle brackets in them are safe. PlaceholderAPI and theme tags run after.
     */
    private String text(Player player, String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        String tagged = template;
        Map<String, String> tags = new HashMap<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            String token = "{" + e.getKey() + "}";
            if (tagged.contains(token)) {
                String tag = "v_" + e.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
                tagged = tagged.replace(token, "<" + tag + ">");
                tags.put(tag, e.getValue() == null ? "" : e.getValue());
            }
        }
        return messages.renderInline(PapiBridge.apply(player, tagged), tags);
    }

    static String fill(String template, Map<String, String> values) {
        if (template == null || template.indexOf('{') < 0) {
            return template == null ? "" : template;
        }
        String out = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static boolean truthy(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return !(v.isEmpty() || v.equals("false") || v.equals("0") || v.equals("no") || v.startsWith("{"));
    }

    private static final class Holder implements InventoryHolder {
        final String menuId;
        final Map<String, String> args;
        final Map<Integer, Click> clicks = new HashMap<>();
        int page;
        int pages = 1;
        Inventory inventory;
        String title = "";
        boolean form;
        /** Counts sends, to tell whether an action re-sent the form. */
        int sends;

        Holder(String menuId, Map<String, String> args, int page) {
            this.menuId = menuId;
            this.args = args;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Click(MenuDef.ItemDef item, Map<String, String> values) { }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Click click = holder.clicks.get(event.getRawSlot());
        if (click == null) {
            return;
        }
        List<String> toRun = click.item().actionsFor(event.getClick());
        MenuClick context = new MenuClick(player, holder.menuId, holder.args, click.values(),
                event.getClick(), this);
        // Run on the next tick so actions can open or close inventories safely.
        Bukkit.getScheduler().runTask(core, () -> {
            for (String raw : toRun) {
                runAction(context, holder, fill(raw, click.values()));
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private record PendingConfirm(String action, MenuClick click) { }

    private final Map<UUID, PendingConfirm> pendingConfirms = new ConcurrentHashMap<>();

    /** Paging actions are ignored since there is no menu page to move. */
    public void runAction(MenuClick click, String raw) {
        runAction(click, null, raw);
    }

    private void runAction(MenuClick click, Holder holder, String raw) {
        String id = raw;
        String argument = "";
        int colon = raw.indexOf(':');
        // "fireball:buy:x" -> id "fireball:buy" when that is registered.
        while (colon > 0) {
            String candidate = raw.substring(0, colon);
            if (actions.containsKey(candidate)) {
                id = candidate;
                argument = raw.substring(colon + 1);
            }
            colon = raw.indexOf(':', colon + 1);
        }
        if (!actions.containsKey(id)) {
            int first = raw.indexOf(':');
            if (first > 0 && actions.containsKey(raw.substring(0, first))) {
                id = raw.substring(0, first);
                argument = raw.substring(first + 1);
            }
        }
        switch (id) {
            case "next-page" -> {
                if (holder != null) {
                    repage(click.player(), holder, holder.page + 1);
                }
            }
            case "previous-page" -> {
                if (holder != null) {
                    repage(click.player(), holder, holder.page - 1);
                }
            }
            default -> {
                MenuAction action = actions.get(id);
                if (action == null) {
                    logger.warning("Menu " + click.menuId() + ": unknown action '" + raw + "'");
                    return;
                }
                action.run(click, argument.trim());
            }
        }
    }

    private void repage(Player player, Holder holder, int page) {
        LoadedMenu menu = menus.get(holder.menuId);
        if (menu == null) {
            return;
        }
        holder.page = page;
        Map<String, String> base = new HashMap<>(holder.args);
        base.put("player", player.getName());
        render(player, menu.def(), holder, base);
        if (holder.form) {
            sendForm(player, holder);
        }
    }

    private void registerBuiltinActions() {
        registerAction("close", (click, arg) -> {
            forms.remove(click.player().getUniqueId());
            click.player().closeInventory();
        });
        registerAction("back", (click, arg) -> {
            Deque<Opened> stack = history.get(click.player().getUniqueId());
            Opened previous = stack == null ? null : stack.poll();
            if (previous == null) {
                click.player().closeInventory();
            } else {
                open(click.player(), previous.menuId(), previous.args(), previous.page(), false);
            }
        });
        registerAction("open-menu", (click, arg) -> {
            String[] parts = arg.trim().split("\\s+");
            Map<String, String> args = new HashMap<>();
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq > 0) {
                    args.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                }
            }
            open(click.player(), parts[0], args);
        });
        registerAction("command", (click, arg) -> click.player().performCommand(stripSlash(arg)));
        registerAction("console", (click, arg) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(arg)));
        registerAction("message", (click, arg) -> messages.sendRaw(click.player(), arg));
        registerAction("sound", (click, arg) -> {
            // Either a Bukkit name (UI_BUTTON_CLICK) or a namespaced key (ui.button.click).
            // Namespaced keys (minecraft:ui.button.click) become UI_BUTTON_CLICK;
            // the platform maps the name to this server version's sound.
            String name = arg.substring(arg.indexOf(':') + 1).replace('.', '_').toUpperCase(Locale.ROOT);
            me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().sounds().play(click.player(), name, 1f, 1f);
        });
        registerAction("none", (click, arg) -> { });
        // confirm:<label>|<action> opens the yes/no menu; yes runs <action>.
        registerAction("confirm", (click, arg) -> {
            int bar = arg.indexOf('|');
            String label = bar < 0 ? "" : arg.substring(0, bar).trim();
            String action = bar < 0 ? arg : arg.substring(bar + 1).trim();
            if (!isEnabled("confirm")) {
                runAction(click, action);
                return;
            }
            pendingConfirms.put(click.player().getUniqueId(), new PendingConfirm(action, click));
            Map<String, String> args = new HashMap<>();
            args.put("label", label);
            open(click.player(), "confirm", args, 0, true);
        });
        registerAction("confirm-yes", (click, arg) -> {
            PendingConfirm pending = pendingConfirms.remove(click.player().getUniqueId());
            if (pending == null) {
                click.player().closeInventory();
                return;
            }
            // Go back to the menu the question came from, then run the action
            // there (it may refresh, open another menu or close).
            Deque<Opened> stack = history.get(click.player().getUniqueId());
            Opened previous = stack == null ? null : stack.poll();
            if (previous != null) {
                open(click.player(), previous.menuId(), previous.args(), previous.page(), false);
            } else {
                click.player().closeInventory();
            }
            MenuClick original = pending.click();
            runAction(new MenuClick(click.player(), original.menuId(), original.args(), original.values(),
                    original.type(), this), pending.action());
        });
        registerAction("refresh", (click, arg) -> {
            Holder h = currentHolder(click.player());
            if (h != null) {
                repage(click.player(), h, h.page);
            }
        });
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private void tickRefresh() {
        ticks += 10;
        long now = ticks;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) {
                continue;
            }
            LoadedMenu menu = menus.get(holder.menuId);
            int every = menu == null ? 0 : menu.def().refreshTicks();
            if (every > 0 && now % every < 10) {
                repage(player, holder, holder.page);
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        history.remove(event.getPlayer().getUniqueId());
        forms.remove(event.getPlayer().getUniqueId());
        pendingConfirms.remove(event.getPlayer().getUniqueId());
    }
}
