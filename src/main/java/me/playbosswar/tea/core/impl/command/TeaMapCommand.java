package me.playbosswar.tea.core.impl.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapDefinition;
import me.playbosswar.tea.core.api.map.MapExtension;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.impl.TeaCorePlugin;
import me.playbosswar.tea.core.impl.map.DraftHandle;
import me.playbosswar.tea.core.impl.map.MapDraft;
import me.playbosswar.tea.core.impl.map.MapSetupSidebar;
import me.playbosswar.tea.core.impl.scoreboard.ScoreboardManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TeaMapCommand {

    static final List<String> SUBCOMMANDS = List.of(
            "create", "edit", "pos1", "pos2", "waiting", "addspawn", "clearspawns",
            "status", "board", "removeextension", "displayname",
            "min", "max", "save", "discard", "list", "info", "reload", "delete", "history", "restore", "import", "help");

    private final TeaCorePlugin plugin;

    public TeaMapCommand(TeaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> showHelp(sender);
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "pos1" -> setPos(sender, 1);
            case "pos2" -> setPos(sender, 2);
            case "waiting" -> waiting(sender);
            case "addspawn" -> addSpawn(sender);
            case "clearspawns" -> clearSpawns(sender);
            case "removeextension" -> removeExtension(sender, args);
            case "displayname" -> displayName(sender, args);
            case "status" -> status(sender);
            case "board" -> board(sender);
            case "min" -> setBound(sender, args, true);
            case "max" -> setBound(sender, args, false);
            case "save" -> save(sender);
            case "discard" -> discard(sender);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "delete" -> delete(sender, args);
            case "history" -> history(sender, args);
            case "restore" -> restore(sender, args);
            case "import" -> importMap(sender, args);
            default -> runExtensionSubcommand(sender, args);
        }
    }

    private void showHelp(CommandSender sender) {
        MessageService ms = plugin.messageService();
        ms.sendToSender(sender, "core.map.help.header");
        ms.sendToSender(sender, "core.map.help.create");
        ms.sendToSender(sender, "core.map.help.edit");
        ms.sendToSender(sender, "core.map.help.pos1");
        ms.sendToSender(sender, "core.map.help.pos2");
        ms.sendToSender(sender, "core.map.help.waiting");
        ms.sendToSender(sender, "core.map.help.addspawn");
        ms.sendToSender(sender, "core.map.help.clearspawns");
        ms.sendToSender(sender, "core.map.help.displayname");
        ms.sendToSender(sender, "core.map.help.min");
        ms.sendToSender(sender, "core.map.help.max");
        ms.sendToSender(sender, "core.map.help.removeextension");
        ms.sendToSender(sender, "core.map.help.status");
        ms.sendToSender(sender, "core.map.help.board");
        ms.sendToSender(sender, "core.map.help.save");
        ms.sendToSender(sender, "core.map.help.discard");
        ms.sendToSender(sender, "core.map.help.list");
        ms.sendToSender(sender, "core.map.help.info");
        ms.sendToSender(sender, "core.map.help.reload");
        ms.sendToSender(sender, "core.map.help.delete");
        ms.sendToSender(sender, "core.map.help.history");
        ms.sendToSender(sender, "core.map.help.restore");
        ms.sendToSender(sender, "core.map.help.hint-extensions");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBCOMMANDS)
                if (s.startsWith(p))
                    out.add(s);
            MapDraft draft = sender instanceof Player pl
                    ? plugin.mapEditingService().currentDraft(pl).orElse(null) : null;
            for (MapExtension<?> ext : plugin.maps().extensions().all()) {
                if (draft != null && !ext.appliesTo(draft.gameId()))
                    continue;
                String sub = ext.subcommand().toLowerCase(Locale.ROOT);
                if (sub.startsWith(p))
                    out.add(sub);
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("create")
                || args[0].equalsIgnoreCase("edit")
                || args[0].equalsIgnoreCase("list")
                || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("history")
                || args[0].equalsIgnoreCase("restore"))) {
            return plugin.games().all().stream()
                    .map(d -> d.id().value())
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("edit")
                || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("delete")
                || args[0].equalsIgnoreCase("history")
                || args[0].equalsIgnoreCase("restore"))) {
            try {
                GameId g = GameId.of(args[1]);
                return plugin.maps().forGame(g).stream()
                        .map(MapDefinition::mapId)
                        .filter(s -> s.startsWith(args[2]))
                        .toList();
            } catch (Exception e) {
                return List.of();
            }
        }
        return List.of();
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!sender.hasPermission("tea.map.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.map.create.usage");
            return;
        }
        if (plugin.arenas().arenaOf(player.getUniqueId()).isPresent()) {
            // The editor world can't be entered from a match; the draft would be stuck.
            plugin.messageService().send(player, "core.map.leave-arena-first");
            return;
        }
        GameId gameId = GameId.of(args[1]);
        if (plugin.games().get(gameId).isEmpty()) {
            plugin.messageService().send(player, "lobby.play.unknown-game", Map.of("game", gameId.value()));
            return;
        }
        String mapId = args[2].toLowerCase(Locale.ROOT);
        try {
            MapDraft draft = plugin.mapEditingService().create(player, gameId, mapId);
            plugin.messageService().send(player, "core.map.create.started", Map.of(
                    "game", gameId.value(), "map", mapId, "world", draft.editWorldName()));
        } catch (Exception e) {
            plugin.messageService().send(player, "core.map.create.failed",
                    Map.of("reason", reasonOf(e)));
        }
    }

    private void edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        if (!sender.hasPermission("tea.map.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.map.edit.usage");
            return;
        }
        if (plugin.arenas().arenaOf(player.getUniqueId()).isPresent()) {
            // The editor world can't be entered from a match; the draft would be stuck.
            plugin.messageService().send(player, "core.map.leave-arena-first");
            return;
        }
        GameId gameId = GameId.of(args[1]);
        String mapId = args[2].toLowerCase(Locale.ROOT);
        try {
            MapDraft draft = plugin.mapEditingService().edit(player, gameId, mapId);
            plugin.messageService().send(player, "core.map.edit.started", Map.of(
                    "game", gameId.value(),
                    "map", mapId,
                    "spawns", String.valueOf(draft.participantSpawns().size()),
                    "world", draft.editWorldName()));
        } catch (Exception e) {
            plugin.messageService().send(player, "core.map.edit.failed",
                    Map.of("reason", reasonOf(e)));
        }
    }

    private void setPos(CommandSender sender, int which) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraftInEditWorld(player);
        if (draft == null)
            return;
        Location here = player.getLocation();
        if (which == 1)
            draft.setPos1(here.clone());
        else
            draft.setPos2(here.clone());
        plugin.messageService().send(player,
                which == 1 ? "core.map.pos1.set" : "core.map.pos2.set",
                Map.of("x", format(here.getX()), "y", format(here.getY()), "z", format(here.getZ())));
    }

    private void waiting(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraftInEditWorld(player);
        if (draft == null)
            return;
        Location loc = player.getLocation();
        SpawnPoint sp = new SpawnPoint("waiting",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        draft.setWaitingSpawn(sp);
        plugin.messageService().send(player, "core.map.waiting.set", Map.of(
                "x", format(loc.getX()), "y", format(loc.getY()), "z", format(loc.getZ())));
    }

    private void addSpawn(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraftInEditWorld(player);
        if (draft == null)
            return;
        Location loc = player.getLocation();
        String id = "s" + draft.participantSpawns().size();
        SpawnPoint sp = new SpawnPoint(id, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        draft.addParticipantSpawn(sp);
        plugin.messageService().send(player, "core.map.addspawn.added", Map.of(
                "id", id,
                "count", String.valueOf(draft.participantSpawns().size()),
                "x", format(loc.getX()), "y", format(loc.getY()), "z", format(loc.getZ())));
    }

    private void runExtensionSubcommand(CommandSender sender, String[] args) {
        String token = args[0].toLowerCase(Locale.ROOT);
        MapExtension<?> ext = plugin.maps().extensions().bySubcommand(token).orElse(null);
        if (ext == null) {
            plugin.messageService().sendToSender(sender, "core.command.unknown");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        MapDraft draft = requireDraftInEditWorld(player);
        if (draft == null)
            return;
        if (!ext.appliesTo(draft.gameId())) {
            plugin.messageService().send(player, "core.map.extension.other-game",
                    Map.of("id", ext.key().id(), "game", draft.gameId().value()));
            return;
        }
        String[] tail = new String[args.length - 1];
        System.arraycopy(args, 1, tail, 0, tail.length);
        try {
            invokeExtension(ext, player, draft, tail);
            plugin.messageService().send(player, "core.map.extension.set",
                    Map.of("id", ext.key().id()));
        } catch (Exception e) {
            plugin.messageService().send(player, "core.map.extension.failed",
                    Map.of("id", ext.key().id(), "reason", reasonOf(e)));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String describeValue(MapExtension<?> ext, Object value) {
        return ((MapExtension) ext).describeValue(value);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void invokeExtension(MapExtension<?> ext, Player player, MapDraft draft, String[] args) {
        DraftHandle handle = new DraftHandle<>(draft, ext.key());
        ((MapExtension) ext).handleCommand(player, handle, args);
    }

    private void displayName(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraft(player);
        if (draft == null)
            return;
        if (args.length < 2) {
            // No name = clear, falling back to map id at render time.
            draft.setDisplayName(null);
            plugin.messageService().send(player, "core.map.displayname.cleared",
                    Map.of("map", draft.mapId()));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1)
                sb.append(' ');
            sb.append(args[i]);
        }
        String name = sb.toString();
        draft.setDisplayName(name);
        plugin.messageService().send(player, "core.map.displayname.set",
                Map.of("map", draft.mapId(), "name", name));
    }

    private void removeExtension(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player))
            return;
        if (args.length < 2) {
            plugin.messageService().send(player, "core.map.removeextension.usage");
            return;
        }
        MapDraft draft = requireDraft(player);
        if (draft == null)
            return;
        String target = args[1];
        String id = plugin.maps().extensions().bySubcommand(target)
                .map(e -> e.key().id())
                .orElse(target);
        if (!draft.extensions().containsKey(id)) {
            plugin.messageService().send(player, "core.map.removeextension.missing",
                    Map.of("id", id));
            return;
        }
        draft.clearExtension(id);
        plugin.messageService().send(player, "core.map.removeextension.done",
                Map.of("id", id));
    }

    private void status(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraft(player);
        if (draft == null)
            return;
        MessageService ms = plugin.messageService();
        ms.send(player, "core.map.status.header",
                Map.of("game", draft.gameId().value(), "map", draft.mapId()));
        java.util.Set<String> missingSet = new java.util.HashSet<>(plugin.mapEditingService().validateDraft(player));
        sendStatusEntry(player, "pos1", !missingSet.contains("pos1"),
                draft.pos1().map(l -> format(l.getX()) + "/" + format(l.getY()) + "/" + format(l.getZ()))
                        .orElse(null));
        sendStatusEntry(player, "pos2", !missingSet.contains("pos2"),
                draft.pos2().map(l -> format(l.getX()) + "/" + format(l.getY()) + "/" + format(l.getZ()))
                        .orElse(null));
        sendStatusEntry(player, "waiting-spawn", !missingSet.contains("waiting-spawn"),
                draft.waitingSpawn().map(s -> format(s.x()) + "/" + format(s.y()) + "/" + format(s.z()))
                        .orElse(null));
        sendStatusEntry(player, "participant-spawns", !missingSet.contains("participant-spawns"),
                draft.participantSpawns().isEmpty() ? null : String.valueOf(draft.participantSpawns().size()));
        // display-name is optional: show it muted when unset, never as a save blocker.
        sendStatusEntry(player, "display-name", true, draft.displayName().orElse(null));
        for (MapExtension<?> ext : plugin.mapEditingService().extensionsFor(draft.gameId())) {
            Object value = draft.extensions().get(ext.key().id());
            boolean satisfied = !missingSet.contains("extension:" + ext.key().id());
            sendStatusEntry(player, ext.statusLabel(), satisfied,
                    value == null ? null : describeValue(ext, value));
        }

        if (missingSet.isEmpty()) {
            ms.send(player, "core.map.status.ready");
        } else {
            ms.send(player, "core.map.status.missing-header");
            for (String field : missingSet) {
                sendMissingLine(player, field);
            }
        }
    }

    private void sendStatusEntry(Player player, String field, boolean satisfied, String value) {
        MessageService ms = plugin.messageService();
        if (value != null) {
            ms.send(player, "core.map.status.entry-set",
                    Map.of("field", field, "value", value));
        } else if (satisfied) {
            ms.send(player, "core.map.status.entry-optional",
                    Map.of("field", field));
        } else {
            ms.send(player, "core.map.status.entry-missing",
                    Map.of("field", field));
        }
    }

    private void board(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().sendToSender(sender, "core.command.player-only");
            return;
        }
        MapSetupSidebar sidebar = plugin.mapSetupSidebar();
        ScoreboardManager scoreboards = plugin.scoreboards();
        if (sidebar == null || scoreboards == null) {
            plugin.messageService().send(player, "core.map.board.disabled");
            return;
        }
        if (requireDraft(player) == null)
            return;
        boolean shown = sidebar.toggle(player);
        scoreboards.refresh(player);
        plugin.messageService().send(player,
                shown ? "core.map.board.shown" : "core.map.board.hidden");
    }

    private void sendMissingLine(Player player, String field) {
        MessageService ms = plugin.messageService();
        if (field.startsWith("extension:")) {
            String id = field.substring("extension:".length());
            String sub = plugin.maps().extensions().bySubcommand(id.replace("ascend:", ""))
                    .map(MapExtension::subcommand)
                    .orElseGet(() -> plugin.maps().extensions().all().stream()
                            .filter(e -> e.key().id().equals(id))
                            .map(MapExtension::subcommand)
                            .findFirst()
                            .orElse(id));
            ms.send(player, "core.map.status.missing.extension", Map.of("id", id, "subcommand", sub));
        } else {
            ms.send(player, "core.map.status.missing." + field);
        }
    }

    private void setBound(CommandSender sender, String[] args, boolean min) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraft(player);
        if (draft == null)
            return;
        if (args.length < 2) {
            plugin.messageService().send(player,
                    min ? "core.map.min.usage" : "core.map.max.usage");
            return;
        }
        int value;
        try {
            value = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.messageService().send(player,
                    min ? "core.map.min.usage" : "core.map.max.usage");
            return;
        }
        if (min) {
            draft.setMinPlayers(value);
        } else {
            draft.setMaxPlayers(value);
        }
        plugin.messageService().send(player,
                min ? "core.map.min.set" : "core.map.max.set",
                Map.of("min", String.valueOf(draft.minPlayers()),
                        "max", String.valueOf(draft.maxPlayers())));
    }

    private void clearSpawns(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraft(player);
        if (draft == null)
            return;
        int before = draft.participantSpawns().size();
        draft.clearParticipantSpawns();
        plugin.messageService().send(player, "core.map.clearspawns.done",
                Map.of("count", String.valueOf(before)));
    }

    private void save(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;
        MapDraft draft = requireDraft(player);
        if (draft == null)
            return;
        List<String> missing = plugin.mapEditingService().validateDraft(player);
        if (!missing.isEmpty()) {
            plugin.messageService().send(player, "core.map.save.missing-header");
            for (String field : missing) {
                sendMissingLine(player, field);
            }
            return;
        }
        try {
            MapDefinition def = plugin.mapEditingService().save(player);
            plugin.messageService().send(player, "core.map.save.success", Map.of(
                    "game", def.gameId().value(),
                    "map", def.mapId(),
                    "spawns", String.valueOf(def.participantSpawns().size())));
        } catch (Exception e) {
            plugin.messageService().send(player, "core.map.save.failed",
                    Map.of("reason", reasonOf(e)));
        }
    }

    private void discard(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;
        if (plugin.mapEditingService().currentDraft(player).isEmpty()) {
            plugin.messageService().send(player, "core.map.discard.none");
            return;
        }
        plugin.mapEditingService().discard(player);
        plugin.messageService().send(player, "core.map.discard.done");
    }

    private void list(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        MapService svc = plugin.maps();
        ms.sendToSender(sender, "core.map.list.header");
        int count = 0;
        if (args.length >= 2) {
            GameId g = GameId.of(args[1]);
            for (MapDefinition m : svc.forGame(g)) {
                ms.sendToSender(sender, "core.map.list.entry", Map.of(
                        "game", g.value(), "map", m.mapId(),
                        "spawns", String.valueOf(m.participantSpawns().size()),
                        "min", String.valueOf(m.minPlayers()),
                        "max", String.valueOf(m.maxPlayers())));
                count++;
            }
        } else {
            for (var def : plugin.games().all()) {
                for (MapDefinition m : svc.forGame(def.id())) {
                    ms.sendToSender(sender, "core.map.list.entry", Map.of(
                            "game", def.id().value(), "map", m.mapId(),
                            "spawns", String.valueOf(m.participantSpawns().size()),
                            "min", String.valueOf(m.minPlayers()),
                            "max", String.valueOf(m.maxPlayers())));
                    count++;
                }
            }
        }
        if (count == 0)
            ms.sendToSender(sender, "core.map.list.empty");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.map.info.usage");
            return;
        }
        GameId g = GameId.of(args[1]);
        MapDefinition m = plugin.maps().get(g, args[2]).orElse(null);
        if (m == null) {
            plugin.messageService().sendToSender(sender, "core.map.info.not-found",
                    Map.of("game", g.value(), "map", args[2]));
            return;
        }
        MessageService ms = plugin.messageService();
        ms.sendToSender(sender, "core.map.info.header",
                Map.of("game", g.value(), "map", m.mapId()));
        ms.sendToSender(sender, "core.map.info.display-name",
                Map.of("name", m.resolvedDisplayName()));
        ms.sendToSender(sender, "core.map.info.schematic",
                Map.of("file", m.schematic().map(Object::toString).orElse("-")));
        ms.sendToSender(sender, "core.map.info.waiting",
                Map.of("waiting", m.waitingSpawn() == null ? "-"
                        : format(m.waitingSpawn().x()) + "/" + format(m.waitingSpawn().y()) + "/"
                                + format(m.waitingSpawn().z())));
        ms.sendToSender(sender, "core.map.info.spawns",
                Map.of("count", String.valueOf(m.participantSpawns().size())));
        ms.sendToSender(sender, "core.map.info.players",
                Map.of("min", String.valueOf(m.minPlayers()),
                        "max", String.valueOf(m.maxPlayers())));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("tea.map.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        int before = plugin.maps().all().size();
        plugin.reloadMaps();
        int after = plugin.maps().all().size();
        plugin.messageService().sendToSender(sender, "core.map.reload.done",
                Map.of("before", String.valueOf(before), "after", String.valueOf(after)));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tea.map.admin")) {
            plugin.messageService().sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messageService().sendToSender(sender, "core.map.delete.usage");
            return;
        }
        GameId g = GameId.of(args[1]);
        try {
            plugin.mapEditingService().delete(g, args[2].toLowerCase(Locale.ROOT), sender.getName());
            plugin.messageService().sendToSender(sender, "core.map.delete.done",
                    Map.of("game", g.value(), "map", args[2]));
        } catch (Exception e) {
            plugin.messageService().sendToSender(sender, "core.map.delete.failed",
                    Map.of("reason", reasonOf(e)));
        }
    }

    private void history(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (!sender.hasPermission("tea.map.admin")) {
            ms.sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 3) {
            ms.sendToSender(sender, "core.map.history.usage");
            return;
        }
        GameId g = GameId.of(args[1]);
        String map = args[2].toLowerCase(Locale.ROOT);
        Map<String, String> base = Map.of("game", g.value(), "map", map);
        List<me.playbosswar.tea.core.impl.map.MapBackups.Version> versions;
        try {
            versions = plugin.mapEditingService().backups().list(g, map);
        } catch (IllegalArgumentException e) {
            versions = List.of();
        }
        if (versions.isEmpty()) {
            ms.sendToSender(sender, "core.map.history.none", base);
            return;
        }
        ms.sendToSender(sender, "core.map.history.header", base);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());
        for (var v : versions) {
            ms.sendToSender(sender, "core.map.history.line", Map.of("n", String.valueOf(v.number()),
                    "when", fmt.format(v.when()), "actor", v.actor(), "reason", v.reason()));
        }
        ms.sendToSender(sender, "core.map.history.hint", Map.of("game", g.value(), "map", map, "n", "1"));
    }

    private void importMap(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (!sender.hasPermission("tea.map.admin")) {
            ms.sendToSender(sender, "core.command.no-permission");
            return;
        }
        if (args.length < 5) {
            ms.sendToSender(sender, "core.map.import.usage");
            return;
        }
        GameId g = GameId.of(args[1]);
        String map = args[2].toLowerCase(Locale.ROOT);
        String pointsName = args[4].replaceAll("[^A-Za-z0-9_.-]", "");
        java.io.File points = new java.io.File(new java.io.File(plugin.getDataFolder(), "imports"),
                pointsName.endsWith(".yml") ? pointsName : pointsName + ".yml");
        if (!points.isFile()) {
            ms.sendToSender(sender, "core.map.import.failed", Map.of("reason", "no points file " + points.getName()
                    + " in plugins/TeaCore/imports"));
            return;
        }
        try {
            if (plugin.mapEditingService().backups() != null) {
                plugin.mapEditingService().backups().snapshot(g, map, sender.getName(), "import");
            }
            var importer = new me.playbosswar.tea.core.impl.map.MapImporter(plugin.mapPersistence(), plugin.getLogger());
            var result = importer.importWorld(g, map, args[3], points);
            plugin.reloadMaps();
            me.playbosswar.tea.core.impl.map.MapChanges.changed();
            ms.sendToSender(sender, "core.map.import.done", Map.of("game", g.value(), "map", map,
                    "size", result.size()[0] + "x" + result.size()[1] + "x" + result.size()[2]));
        } catch (Exception e) {
            ms.sendToSender(sender, "core.map.import.failed", Map.of("reason", reasonOf(e)));
        }
    }

    private void restore(CommandSender sender, String[] args) {
        MessageService ms = plugin.messageService();
        if (!sender.hasPermission("tea.map.admin")) {
            ms.sendToSender(sender, "core.command.no-permission");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(args.length < 4 ? "" : args[3]);
        } catch (NumberFormatException e) {
            ms.sendToSender(sender, "core.map.restore.usage");
            return;
        }
        GameId g = GameId.of(args[1]);
        String map = args[2].toLowerCase(Locale.ROOT);
        Map<String, String> values = Map.of("game", g.value(), "map", map, "n", String.valueOf(n));
        try {
            plugin.mapEditingService().restore(g, map, n, sender.getName());
            ms.sendToSender(sender, "core.map.restore.done", values);
            plugin.discordEvents().staff(sender.getName(), "restored map " + g.value() + "/" + map + " to version #" + n);
        } catch (IllegalStateException e) {
            ms.sendToSender(sender, "editing".equals(e.getMessage())
                    ? "core.map.restore.editing" : "core.map.restore.no-version", values);
        } catch (IllegalArgumentException e) {
            ms.sendToSender(sender, "core.map.restore.no-version", values);
        } catch (Exception e) {
            ms.sendToSender(sender, "core.map.restore.failed", Map.of("reason", reasonOf(e)));
        }
    }

    private MapDraft requireDraft(Player player) {
        MapDraft d = plugin.mapEditingService().currentDraft(player).orElse(null);
        if (d == null)
            plugin.messageService().send(player, "core.map.no-active-draft");
        return d;
    }

    private MapDraft requireDraftInEditWorld(Player player) {
        MapDraft d = requireDraft(player);
        if (d == null)
            return null;
        if (!player.getWorld().getName().equals(d.editWorldName())) {
            plugin.messageService().send(player, "core.map.not-in-edit-world",
                    Map.of("world", d.editWorldName()));
            return null;
        }
        return d;
    }

    private static String reasonOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
