package me.playbosswar.tea.core.impl.map.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.playbosswar.tea.core.api.feature.FeatureService;
import me.playbosswar.tea.core.api.map.MapExtension;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.map.DraftHandle;
import me.playbosswar.tea.core.impl.map.MapDraft;
import me.playbosswar.tea.core.impl.map.MapEditingService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Starting a draft stores the player's inventory and gives them the tools; saving or
 * discarding gives it back. Off when {@code features.map-editor-tools} is false; the
 * commands keep working either way.
 */
public final class MapEditorTools implements Listener, MapEditingService.DraftListener {

    static final int SLOT_REGION = 0;
    static final int SLOT_POINT = 1;
    static final int SLOT_CHECKLIST = 7;
    static final int SLOT_SAVE = 8;

    private static final double REMOVE_RADIUS = 1.5;
    private static final double REMOVE_REACH = 10.0;
    private static final int MAX_OUTLINE_PARTICLES = 600;

    private final Plugin plugin;
    private final MapEditingService editing;
    private final MessageService messages;
    private final FeatureService features;
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> selectedType = new ConcurrentHashMap<>();
    private final Set<UUID> markersHidden = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<me.playbosswar.tea.core.api.platform.Holograms.Hologram>> labels = new ConcurrentHashMap<>();
    private final Map<UUID, String> labelSignatures = new ConcurrentHashMap<>();
    private MenuService menus;
    private BukkitTask task;

    public MapEditorTools(Plugin plugin, MapEditingService editing, MessageService messages,
            FeatureService features) {
        this.plugin = plugin;
        this.editing = editing;
        this.messages = messages;
        this.features = features;
        me.playbosswar.tea.core.impl.platform.LockedItems.register(s -> toolOf(s) != null);
    }

    public void start(MenuService menus) {
        this.menus = menus;
        editing.addListener(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        new EditorMenus(this, editing, messages).register(menus);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::drawMarkers, 20L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreInventory(player);
            clearLabels(player.getUniqueId());
        }
    }

    private boolean enabled() {
        return features.enabled("map-editor-tools");
    }

    @Override
    public void started(Player player, MapDraft draft) {
        if (!enabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!savedInventories.containsKey(id)) {
            savedInventories.put(id, cloneAll(me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().storage(player)));
            savedArmor.put(id, cloneAll(player.getInventory().getArmorContents()));
        }
        selectedType.put(id, 0);
        player.getInventory().clear();
        giveTools(player, draft);
        messages.send(player, "core.map.editor.started");
    }

    @Override
    public void ended(Player player, MapDraft draft) {
        clearLabels(player.getUniqueId());
        selectedType.remove(player.getUniqueId());
        restoreInventory(player);
    }

    private void restoreInventory(Player player) {
        ItemStack[] storage = savedInventories.remove(player.getUniqueId());
        ItemStack[] armor = savedArmor.remove(player.getUniqueId());
        if (storage == null) {
            return;
        }
        player.getInventory().clear();
        me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().setStorage(player, storage);
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] == null ? null : items[i].clone();
        }
        return out;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Put the real inventory back before the server saves player data; the
        // tools come back on rejoin while the draft is still open.
        Player player = event.getPlayer();
        clearLabels(player.getUniqueId());
        lastLeftClick.remove(player.getUniqueId());
        restoreInventory(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                editing.currentDraft(player).ifPresent(d -> started(player, d));
            }
        }, 2L);
    }

    enum Tool { REGION, POINT, CHECKLIST, SAVE }

    void giveTools(Player player, MapDraft draft) {
        String locale = messages.resolveLocale(player);
        player.getInventory().setItem(SLOT_REGION, tool(Tool.REGION, Material.BLAZE_ROD,
                text(locale, "core.map.editor.tool.region.name", Map.of()),
                messages.renderLegacyLines(locale, "core.map.editor.tool.region.lore", Map.of())));
        refreshPointTool(player, draft);
        player.getInventory().setItem(SLOT_CHECKLIST, tool(Tool.CHECKLIST, mat("WRITABLE_BOOK"),
                text(locale, "core.map.editor.tool.checklist.name", Map.of()),
                messages.renderLegacyLines(locale, "core.map.editor.tool.checklist.lore", Map.of())));
        player.getInventory().setItem(SLOT_SAVE, tool(Tool.SAVE, Material.EMERALD,
                text(locale, "core.map.editor.tool.save.name", Map.of()),
                messages.renderLegacyLines(locale, "core.map.editor.tool.save.lore", Map.of())));
    }

    void refreshPointTool(Player player, MapDraft draft) {
        if (!savedInventories.containsKey(player.getUniqueId())) {
            return;
        }
        List<PointType> types = types(player, draft);
        PointType type = types.get(selected(player, types.size()));
        String locale = messages.resolveLocale(player);
        Map<String, String> values = Map.of(
                "type", type.label(),
                "count", String.valueOf(type.points(draft).size()),
                "index", String.valueOf(selected(player, types.size()) + 1),
                "total", String.valueOf(types.size()));
        player.getInventory().setItem(SLOT_POINT, tool(Tool.POINT, type.icon(),
                text(locale, "core.map.editor.tool.point.name", values),
                messages.renderLegacyLines(locale, "core.map.editor.tool.point.lore", values)));
    }

    /** A material by modern name for this server version. */
    private static Material mat(String modernName) {
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items().material(modernName).orElse(Material.PAPER);
    }

    private ItemStack tool(Tool tool, Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return me.playbosswar.tea.core.impl.platform.TeaTags.set(stack, "editor_tool", tool.name());
    }

    private Tool toolOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = me.playbosswar.tea.core.impl.platform.TeaTags.get(stack, "editor_tool");
        if (raw == null) {
            return null;
        }
        try {
            return Tool.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String text(String locale, String key, Map<String, String> values) {
        return messages.renderLegacyNoPrefix(locale, key, values);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().isMainHand(event) || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Tool tool = toolOf(event.getItem());
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        MapDraft draft = editing.currentDraft(player).orElse(null);
        if (draft == null) {
            // A tool left over from a crashed session: just drop it.
            me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().setMainHand(player, null);
            return;
        }
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (left && debounced(player)) {
            // One click can arrive as several events (dig start/abort, swing).
            return;
        }
        Block block = event.getClickedBlock();
        switch (tool) {
            case REGION -> {
                if (block == null) {
                    return;
                }
                if (!inEditWorld(player, draft)) {
                    return;
                }
                Location at = block.getLocation();
                if (left) {
                    draft.setPos1(at);
                } else {
                    draft.setPos2(at);
                }
                messages.send(player, left ? "core.map.pos1.set" : "core.map.pos2.set", Map.of(
                        "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()),
                        "z", String.valueOf(at.getBlockZ())));
            }
            case POINT -> {
                if (!inEditWorld(player, draft)) {
                    return;
                }
                if (player.isSneaking()) {
                    cycle(player, draft, left ? -1 : 1);
                } else if (left) {
                    removeTargeted(player, draft);
                } else {
                    place(player, draft, block);
                }
            }
            case CHECKLIST -> openChecklist(player);
            case SAVE -> player.performCommand("tea map save");
        }
    }

    private static final long LEFT_CLICK_DEBOUNCE_MS = 200;
    private final Map<UUID, Long> lastLeftClick = new ConcurrentHashMap<>();

    private boolean debounced(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastLeftClick.put(player.getUniqueId(), now);
        return last != null && now - last < LEFT_CLICK_DEBOUNCE_MS;
    }

    void openChecklist(Player player) {
        if (menus == null || !menus.open(player, "map-editor", Map.of())) {
            player.performCommand("tea map status");
        }
    }

    private boolean inEditWorld(Player player, MapDraft draft) {
        if (player.getWorld().getName().equals(draft.editWorldName())) {
            return true;
        }
        messages.send(player, "core.map.not-in-edit-world", Map.of("world", draft.editWorldName()));
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (toolOf(me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().players().mainHand(event.getPlayer())) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (toolOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onScroll(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getPreviousSlot() != SLOT_POINT) {
            return;
        }
        if (toolOf(player.getInventory().getItem(SLOT_POINT)) != Tool.POINT) {
            return;
        }
        MapDraft draft = editing.currentDraft(player).orElse(null);
        if (draft == null) {
            return;
        }
        int diff = event.getNewSlot() - event.getPreviousSlot();
        if (diff > 4) {
            diff -= 9;
        } else if (diff < -4) {
            diff += 9;
        }
        event.setCancelled(true);
        cycle(player, draft, diff >= 0 ? 1 : -1);
    }

    interface PointType {
        String id();

        String label();

        Material icon();

        Color color();

        boolean required();

        /** Points go on the clicked block rather than on top of it. */
        default boolean onClickedBlock() {
            return false;
        }

        List<SpawnPoint> points(MapDraft draft);

        void place(Player player, MapDraft draft, SpawnPoint point);

        void remove(MapDraft draft, int index);

        void clear(MapDraft draft);
    }

    List<PointType> types(Player player, MapDraft draft) {
        String locale = messages.resolveLocale(player);
        List<PointType> out = new ArrayList<>();
        out.add(new SpawnsType(text(locale, "core.map.editor.type.spawns", Map.of())));
        out.add(new WaitingType(text(locale, "core.map.editor.type.waiting", Map.of())));
        for (MapExtension<?> ext : editing.extensionsFor(draft.gameId())) {
            out.add(new ExtensionType(ext));
        }
        return out;
    }

    int selected(Player player, int size) {
        int i = selectedType.getOrDefault(player.getUniqueId(), 0);
        return Math.floorMod(i, Math.max(1, size));
    }

    void select(Player player, MapDraft draft, int index) {
        List<PointType> types = types(player, draft);
        selectedType.put(player.getUniqueId(), Math.floorMod(index, types.size()));
        refreshPointTool(player, draft);
        if (savedInventories.containsKey(player.getUniqueId())) {
            player.getInventory().setHeldItemSlot(SLOT_POINT);
        }
        PointType type = types.get(selected(player, types.size()));
        messages.send(player, "core.map.editor.selected", Map.of("type", type.label()));
    }

    private void cycle(Player player, MapDraft draft, int step) {
        select(player, draft, selectedType.getOrDefault(player.getUniqueId(), 0) + step);
    }

    private void place(Player player, MapDraft draft, Block block) {
        List<PointType> types = types(player, draft);
        PointType type = types.get(selected(player, types.size()));
        Location loc = player.getLocation();
        int up = type.onClickedBlock() ? 0 : 1;
        SpawnPoint point = block == null
                ? new SpawnPoint(type.id(), loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5,
                        snapYaw(loc.getYaw()), 0f)
                : new SpawnPoint(type.id(), block.getX() + 0.5, block.getY() + up, block.getZ() + 0.5,
                        snapYaw(loc.getYaw()), 0f);
        type.place(player, draft, point);
        refreshPointTool(player, draft);
        messages.send(player, "core.map.editor.placed", Map.of(
                "type", type.label(),
                "count", String.valueOf(type.points(draft).size()),
                "x", fmt(point.x()), "y", fmt(point.y()), "z", fmt(point.z())));
    }

    /** Spawns face the nearest 45 degrees, so players don't spawn looking slightly off. */
    private static float snapYaw(float yaw) {
        return Math.round(yaw / 45f) * 45f;
    }

    private void removeTargeted(Player player, MapDraft draft) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector dir = eye.getDirection().normalize();
        PointType bestType = null;
        int bestIndex = -1;
        double bestScore = Double.MAX_VALUE;
        for (PointType type : types(player, draft)) {
            List<SpawnPoint> points = type.points(draft);
            for (int i = 0; i < points.size(); i++) {
                SpawnPoint p = points.get(i);
                // Aim at the middle of the marker column.
                Vector centre = new Vector(p.x(), p.y() + 1.0, p.z());
                Vector to = centre.clone().subtract(origin);
                double along = to.dot(dir);
                if (along < 0 || along > REMOVE_REACH) {
                    continue;
                }
                double off = to.clone().subtract(dir.clone().multiply(along)).length();
                if (off > REMOVE_RADIUS) {
                    continue;
                }
                double score = off * 4 + along * 0.1;
                if (score < bestScore) {
                    bestScore = score;
                    bestType = type;
                    bestIndex = i;
                }
            }
        }
        if (bestType == null) {
            messages.send(player, "core.map.editor.nothing-targeted");
            return;
        }
        String label = bestType.points(draft).get(bestIndex).id();
        bestType.remove(draft, bestIndex);
        refreshPointTool(player, draft);
        messages.send(player, "core.map.editor.removed", Map.of(
                "type", bestType.label(), "point", label,
                "count", String.valueOf(bestType.points(draft).size())));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static final class SpawnsType implements PointType {
        private final String label;

        SpawnsType(String label) {
            this.label = label;
        }

        @Override
        public String id() {
            return "spawns";
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public Material icon() {
            return mat("RED_BED");
        }

        @Override
        public Color color() {
            return Color.LIME;
        }

        @Override
        public boolean required() {
            return true;
        }

        @Override
        public List<SpawnPoint> points(MapDraft draft) {
            return draft.participantSpawns();
        }

        @Override
        public void place(Player player, MapDraft draft, SpawnPoint p) {
            draft.addParticipantSpawn(new SpawnPoint("s" + draft.participantSpawns().size(),
                    p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
        }

        @Override
        public void remove(MapDraft draft, int index) {
            List<SpawnPoint> keep = new ArrayList<>(draft.participantSpawns());
            keep.remove(index);
            draft.clearParticipantSpawns();
            for (int i = 0; i < keep.size(); i++) {
                SpawnPoint p = keep.get(i);
                draft.addParticipantSpawn(new SpawnPoint("s" + i, p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
            }
        }

        @Override
        public void clear(MapDraft draft) {
            draft.clearParticipantSpawns();
        }
    }

    static final class WaitingType implements PointType {
        private final String label;

        WaitingType(String label) {
            this.label = label;
        }

        @Override
        public String id() {
            return "waiting";
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public Material icon() {
            return mat("CLOCK");
        }

        @Override
        public Color color() {
            return Color.AQUA;
        }

        @Override
        public boolean required() {
            return true;
        }

        @Override
        public List<SpawnPoint> points(MapDraft draft) {
            return draft.waitingSpawn().map(List::of).orElse(List.of());
        }

        @Override
        public void place(Player player, MapDraft draft, SpawnPoint p) {
            draft.setWaitingSpawn(new SpawnPoint("waiting", p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
        }

        @Override
        public void remove(MapDraft draft, int index) {
            draft.setWaitingSpawn(null);
        }

        @Override
        public void clear(MapDraft draft) {
            draft.setWaitingSpawn(null);
        }
    }

    /** Wraps a game's {@link MapExtension}; raw types because the value type varies. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static final class ExtensionType implements PointType {
        private final MapExtension ext;

        ExtensionType(MapExtension<?> ext) {
            this.ext = ext;
        }

        @Override
        public String id() {
            return ext.key().id();
        }

        @Override
        public String label() {
            return ext.statusLabel();
        }

        @Override
        public Material icon() {
            return ext.editorIcon();
        }

        @Override
        public Color color() {
            return ext.markerColor();
        }

        @Override
        public boolean required() {
            return ext.required();
        }

        @Override
        public boolean onClickedBlock() {
            return ext.pointOnClickedBlock();
        }

        @Override
        public List<SpawnPoint> points(MapDraft draft) {
            Object value = draft.extensions().get(ext.key().id());
            if (value == null) {
                return List.of();
            }
            try {
                List<SpawnPoint> points = ext.points(value);
                return points == null ? List.of() : points;
            } catch (RuntimeException e) {
                return List.of();
            }
        }

        @Override
        public void place(Player player, MapDraft draft, SpawnPoint point) {
            Object current = draft.extensions().get(ext.key().id());
            Object next = ext.placePoint(current, point);
            if (next != null) {
                draft.setRawExtension(ext.key().id(), next);
                return;
            }
            // No position-based placement: fall back to the command handler,
            // which reads the player's own location.
            ext.handleCommand(player, new DraftHandle<>(draft, ext.key()), new String[0]);
        }

        @Override
        public void remove(MapDraft draft, int index) {
            Object current = draft.extensions().get(ext.key().id());
            if (current == null) {
                return;
            }
            Object next = ext.removePoint(current, index);
            if (next == null) {
                draft.clearExtension(ext.key().id());
            } else {
                draft.setRawExtension(ext.key().id(), next);
            }
        }

        @Override
        public void clear(MapDraft draft) {
            draft.clearExtension(ext.key().id());
        }
    }

    boolean markersShown(Player player) {
        return !markersHidden.contains(player.getUniqueId());
    }

    void toggleMarkers(Player player) {
        UUID id = player.getUniqueId();
        if (!markersHidden.remove(id)) {
            markersHidden.add(id);
            clearLabels(id);
        }
    }

    private void drawMarkers() {
        if (!enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            MapDraft draft = editing.currentDraft(player).orElse(null);
            UUID id = player.getUniqueId();
            if (draft == null || markersHidden.contains(id)
                    || !player.getWorld().getName().equals(draft.editWorldName())) {
                clearLabels(id);
                continue;
            }
            World world = player.getWorld();
            List<Label> wanted = new ArrayList<>();
            for (PointType type : types(player, draft)) {
                List<SpawnPoint> points = type.points(draft);
                for (int i = 0; i < points.size(); i++) {
                    SpawnPoint p = points.get(i);
                    for (double dy = 0.1; dy <= 1.9; dy += 0.45) {
                        me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().particles().dust(new Location(world, p.x(), p.y() + dy, p.z()), type.color(), 1.3f, 1, 0, player);
                    }
                    String text = points.size() == 1 && !(type instanceof SpawnsType)
                            ? type.label() : type.label() + " #" + (i + 1);
                    wanted.add(new Label(p.x(), p.y() + 2.3, p.z(), "§f" + text));
                }
            }
            drawRegion(player, draft);
            syncLabels(player, world, wanted);
        }
    }

    private void drawRegion(Player player, MapDraft draft) {
        var particles = me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().particles();
        draft.pos1().ifPresent(l -> particles.dust(l.getBlock().getLocation().add(0.5, 0.5, 0.5), Color.ORANGE, 1.6f, 6, 0.3, player));
        draft.pos2().ifPresent(l -> particles.dust(l.getBlock().getLocation().add(0.5, 0.5, 0.5), Color.ORANGE, 1.6f, 6, 0.3, player));
        if (draft.pos1().isEmpty() || draft.pos2().isEmpty()) {
            return;
        }
        Location a = draft.pos1().get();
        Location b = draft.pos2().get();
        double x1 = Math.min(a.getBlockX(), b.getBlockX());
        double y1 = Math.min(a.getBlockY(), b.getBlockY());
        double z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        double x2 = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double y2 = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        double z2 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        double perimeter = 4 * ((x2 - x1) + (y2 - y1) + (z2 - z1));
        double step = Math.max(1.0, perimeter / MAX_OUTLINE_PARTICLES);
        double[][] ends = {
                { x1, y1, z1, x2, y1, z1 }, { x1, y2, z1, x2, y2, z1 }, { x1, y1, z2, x2, y1, z2 }, { x1, y2, z2, x2, y2, z2 },
                { x1, y1, z1, x1, y2, z1 }, { x2, y1, z1, x2, y2, z1 }, { x1, y1, z2, x1, y2, z2 }, { x2, y1, z2, x2, y2, z2 },
                { x1, y1, z1, x1, y1, z2 }, { x2, y1, z1, x2, y1, z2 }, { x1, y2, z1, x1, y2, z2 }, { x2, y2, z1, x2, y2, z2 } };
        Location eye = player.getLocation();
        for (double[] e : ends) {
            double len = Math.abs(e[3] - e[0]) + Math.abs(e[4] - e[1]) + Math.abs(e[5] - e[2]);
            for (double t = 0; t <= len; t += step) {
                double f = len == 0 ? 0 : t / len;
                double x = e[0] + (e[3] - e[0]) * f;
                double y = e[1] + (e[4] - e[1]) * f;
                double z = e[2] + (e[5] - e[2]) * f;
                // Clients drop particles far away anyway; skip the send.
                if (eye.distanceSquared(new Location(eye.getWorld(), x, y, z)) < 96 * 96) {
                    particles.dust(new Location(eye.getWorld(), x, y, z), Color.WHITE, 0.8f, 1, 0, player);
                }
            }
        }
    }

    private record Label(double x, double y, double z, String text) { }

    private void syncLabels(Player player, World world, List<Label> wanted) {
        UUID id = player.getUniqueId();
        String signature = world.getName() + wanted;
        if (signature.equals(labelSignatures.get(id))) {
            return;
        }
        clearLabels(id);
        List<me.playbosswar.tea.core.api.platform.Holograms.Hologram> spawned = new ArrayList<>();
        for (Label label : wanted) {
            Location at = new Location(world, label.x(), label.y(), label.z());
            spawned.add(me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().holograms().create(at, List.of(label.text())));
        }
        labels.put(id, spawned);
        labelSignatures.put(id, signature);
    }

    private void clearLabels(UUID id) {
        labelSignatures.remove(id);
        List<me.playbosswar.tea.core.api.platform.Holograms.Hologram> old = labels.remove(id);
        if (old != null) {
            old.forEach(me.playbosswar.tea.core.api.platform.Holograms.Hologram::remove);
        }
    }

    Map<String, String> pointValues(Player player, MapDraft draft, int index, PointType type) {
        Map<String, String> v = new HashMap<>();
        int count = type.points(draft).size();
        String locale = messages.resolveLocale(player);
        v.put("type_index", String.valueOf(index));
        v.put("type_id", type.id());
        v.put("label", type.label());
        v.put("count", String.valueOf(count));
        v.put("required", String.valueOf(type.required()));
        v.put("selected", String.valueOf(selected(player, types(player, draft).size()) == index));
        String status = count > 0 ? "set" : type.required() ? "missing" : "optional";
        v.put("status", status);
        v.put("status_label", text(locale, "core.map.editor.status." + status, Map.of()));
        return v;
    }

    MenuEntry pointEntry(Player player, MapDraft draft, int index, PointType type) {
        return new MenuEntry(pointValues(player, draft, index, type), new ItemStack(type.icon()));
    }
}
