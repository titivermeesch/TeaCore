package me.playbosswar.tea.core.impl.map.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import me.playbosswar.tea.core.api.menu.MenuClick;
import me.playbosswar.tea.core.api.menu.MenuEntry;
import me.playbosswar.tea.core.api.menu.MenuService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.map.MapDraft;
import me.playbosswar.tea.core.impl.map.MapEditingService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Sources: {@code map-editor-points}, {@code map-editor-info}.
 * Actions: {@code editor-select:<type index>}, {@code editor-tp:<type index>},
 * {@code editor-clear:<type index>}, {@code editor-region-tp:<1|2>},
 * {@code editor-min:<delta>}, {@code editor-max:<delta>},
 * {@code editor-markers}, {@code editor-save}, {@code editor-discard}.
 */
final class EditorMenus {

    private final MapEditorTools tools;
    private final MapEditingService editing;
    private final MessageService messages;

    EditorMenus(MapEditorTools tools, MapEditingService editing, MessageService messages) {
        this.tools = tools;
        this.editing = editing;
        this.messages = messages;
    }

    void register(MenuService menus) {
        menus.registerSource("map-editor-points", (viewer, args) -> points(viewer));
        menus.registerSource("map-editor-info", (viewer, args) -> info(viewer));
        menus.registerAction("editor-select", (click, arg) -> withDraft(click, draft -> {
            click.player().closeInventory();
            tools.select(click.player(), draft, parse(arg, 0));
        }));
        menus.registerAction("editor-tp", (click, arg) -> withDraft(click, draft -> {
            List<MapEditorTools.PointType> types = tools.types(click.player(), draft);
            int index = parse(arg, -1);
            if (index < 0 || index >= types.size()) {
                return;
            }
            List<SpawnPoint> points = types.get(index).points(draft);
            if (points.isEmpty()) {
                messages.send(click.player(), "core.map.editor.no-points",
                        Map.of("type", types.get(index).label()));
                return;
            }
            teleport(click.player(), draft, points.get(0));
        }));
        menus.registerAction("editor-clear", (click, arg) -> withDraft(click, draft -> {
            List<MapEditorTools.PointType> types = tools.types(click.player(), draft);
            int index = parse(arg, -1);
            if (index < 0 || index >= types.size()) {
                return;
            }
            MapEditorTools.PointType type = types.get(index);
            type.clear(draft);
            tools.refreshPointTool(click.player(), draft);
            messages.send(click.player(), "core.map.editor.cleared", Map.of("type", type.label()));
            click.refresh();
        }));
        menus.registerAction("editor-region-tp", (click, arg) -> withDraft(click, draft -> {
            (parse(arg, 1) == 2 ? draft.pos2() : draft.pos1()).ifPresent(l -> {
                click.player().closeInventory();
                click.player().teleport(l.clone().add(0.5, 1, 0.5));
            });
        }));
        menus.registerAction("editor-min", (click, arg) -> withDraft(click, draft -> {
            draft.setMinPlayers(Math.max(1, draft.minPlayers() + parse(arg, 0)));
            if (draft.maxPlayers() < draft.minPlayers()) {
                draft.setMaxPlayers(draft.minPlayers());
            }
            click.refresh();
        }));
        menus.registerAction("editor-max", (click, arg) -> withDraft(click, draft -> {
            draft.setMaxPlayers(Math.max(draft.minPlayers(), draft.maxPlayers() + parse(arg, 0)));
            click.refresh();
        }));
        menus.registerAction("editor-markers", (click, arg) -> {
            tools.toggleMarkers(click.player());
            messages.send(click.player(), tools.markersShown(click.player())
                    ? "core.map.editor.markers-shown" : "core.map.editor.markers-hidden");
            click.refresh();
        });
        menus.registerAction("editor-save", (click, arg) -> {
            click.player().closeInventory();
            click.player().performCommand("tea map save");
        });
        menus.registerAction("editor-discard", (click, arg) -> {
            click.player().closeInventory();
            click.player().performCommand("tea map discard");
        });
    }

    private interface DraftAction {
        void run(MapDraft draft);
    }

    private void withDraft(MenuClick click, DraftAction action) {
        editing.currentDraft(click.player()).ifPresentOrElse(action::run,
                () -> {
                    click.player().closeInventory();
                    messages.send(click.player(), "core.map.no-active-draft");
                });
    }

    private static int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim().replace("+", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void teleport(Player player, MapDraft draft, SpawnPoint point) {
        World world = org.bukkit.Bukkit.getWorld(draft.editWorldName());
        if (world == null) {
            return;
        }
        player.closeInventory();
        player.teleport(new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
    }

    private List<MenuEntry> points(Player viewer) {
        MapDraft draft = editing.currentDraft(viewer).orElse(null);
        if (draft == null) {
            return List.of();
        }
        List<MenuEntry> out = new ArrayList<>();
        List<MapEditorTools.PointType> types = tools.types(viewer, draft);
        for (int i = 0; i < types.size(); i++) {
            out.add(tools.pointEntry(viewer, draft, i, types.get(i)));
        }
        return out;
    }

    private List<MenuEntry> info(Player viewer) {
        MapDraft draft = editing.currentDraft(viewer).orElse(null);
        if (draft == null) {
            return List.of();
        }
        String locale = messages.resolveLocale(viewer);
        List<MenuEntry> out = new ArrayList<>();

        boolean region = draft.pos1().isPresent() && draft.pos2().isPresent();
        Map<String, String> r = base("region", locale, region ? "set" : "missing");
        r.put("label", text(locale, "core.map.editor.info.region.label"));
        r.put("detail", region ? size(draft) : text(locale, "core.map.editor.info.region.unset"));
        r.put("hint", text(locale, "core.map.editor.info.region.hint"));
        r.put("left_action", draft.pos1().isPresent() ? "editor-region-tp:1" : "none");
        r.put("right_action", draft.pos2().isPresent() ? "editor-region-tp:2" : "none");
        out.add(new MenuEntry(r, new ItemStack(Material.BLAZE_ROD)));

        Map<String, String> p = base("players", locale, "set");
        p.put("label", text(locale, "core.map.editor.info.players.label"));
        p.put("detail", draft.minPlayers() + " - " + draft.maxPlayers());
        p.put("hint", text(locale, "core.map.editor.info.players.hint"));
        p.put("left_action", "editor-min:+1");
        p.put("right_action", "editor-min:-1");
        p.put("shift_left_action", "editor-max:+1");
        p.put("shift_right_action", "editor-max:-1");
        out.add(new MenuEntry(p, items().stackOr("PLAYER_HEAD", "PAPER", 1)));

        Map<String, String> n = base("name", locale, draft.displayName().isPresent() ? "set" : "optional");
        n.put("label", text(locale, "core.map.editor.info.name.label"));
        n.put("detail", draft.displayName().orElse(draft.mapId()));
        n.put("hint", text(locale, "core.map.editor.info.name.hint"));
        out.add(new MenuEntry(n, new ItemStack(Material.NAME_TAG)));

        List<String> missing = editing.validateDraft(viewer);
        Map<String, String> s = base("ready", locale, missing.isEmpty() ? "set" : "missing");
        s.put("label", text(locale, missing.isEmpty()
                ? "core.map.editor.info.ready.yes" : "core.map.editor.info.ready.no"));
        s.put("detail", String.valueOf(missing.size()));
        s.put("hint", text(locale, "core.map.editor.info.ready.hint"));
        s.put("left_action", "editor-save");
        out.add(new MenuEntry(s, items().stackOr(missing.isEmpty() ? "LIME_DYE" : "GRAY_DYE", "PAPER", 1)));
        return out;
    }

    private static me.playbosswar.tea.core.api.platform.Items items() {
        return me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items();
    }

    private Map<String, String> base(String id, String locale, String status) {
        Map<String, String> v = new HashMap<>();
        v.put("info_id", id);
        v.put("status", status);
        v.put("status_label", text(locale, "core.map.editor.status." + status));
        v.put("left_action", "none");
        v.put("right_action", "none");
        v.put("shift_left_action", "none");
        v.put("shift_right_action", "none");
        return v;
    }

    private static String size(MapDraft draft) {
        Location a = draft.pos1().get();
        Location b = draft.pos2().get();
        return String.format(Locale.ROOT, "%dx%dx%d",
                Math.abs(a.getBlockX() - b.getBlockX()) + 1,
                Math.abs(a.getBlockY() - b.getBlockY()) + 1,
                Math.abs(a.getBlockZ() - b.getBlockZ()) + 1);
    }

    private String text(String locale, String key) {
        return messages.renderLegacyNoPrefix(locale, key, Map.of());
    }
}
