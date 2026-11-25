package me.playbosswar.tea.core.impl.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.coordination.ControlCommand;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.coordination.CoordinationEvent;
import me.playbosswar.tea.core.api.settings.SettingsService;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Field;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Resolved;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Type;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Each server publishes the schemas and values of the files it can edit, so the
 * dashboard on the lobby can show game settings the lobby itself doesn't load.
 * A change is validated once, then written by every server that has the file.
 */
public final class SettingsServiceImpl implements SettingsService {

    private static final long REMOTE_TTL_MS = 90_000;

    public record Change(String path, @Nullable Object value) { }

    /**
     * One settings file as staff see it: merged over every server that has it.
     *
     * @param divergent top-level paths whose value differs between servers
     */
    public record FileView(SettingsSchema schema, String plugin, List<String> hosts, Map<String, Object> values,
                           Set<String> divergent) {

        /** The value at any path, including map entries and list items such as {@code kits.runner.gear[0]}. */
        public @Nullable Object value(String path) {
            return SettingsValues.at(document(), path);
        }

        /** The values as a document: each top-level setting at its path. */
        public Map<String, Object> document() {
            Map<String, Object> doc = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getValue() != null) {
                    SettingsValues.put(doc, e.getKey(), SettingsValues.deepCopy(e.getValue()));
                }
            }
            return doc;
        }
    }

    private record Local(Plugin owner, SettingsSchema schema, File file, Consumer<Set<String>> onChange) { }

    private record Remote(SettingsSchema schema, String plugin, Map<String, Object> values, long at) { }

    private record RemoteServer(String group, Map<String, Remote> files) { }

    private final Plugin plugin;
    private final CoordinationBackend coordination;
    private final Logger logger;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Local> local = new ConcurrentHashMap<>();
    private final Map<String, RemoteServer> remote = new ConcurrentHashMap<>();
    private final java.util.function.Supplier<String> group;
    private volatile boolean publishPending;
    private volatile @Nullable SettingsStore store;

    /** @param group this server's group (network.group) */
    public SettingsServiceImpl(Plugin plugin, CoordinationBackend coordination, Logger logger,
                               java.util.function.Supplier<String> group) {
        this.group = group;
        this.plugin = plugin;
        this.coordination = coordination;
        this.logger = logger;
    }

    /** Keeps changes in the database so servers that start later get them. */
    public void setStore(SettingsStore store) {
        this.store = store;
    }

    public void start() {
        coordination.subscribeEvents(this::onEvent);
        Bukkit.getScheduler().runTaskTimer(plugin, this::publish, 100L, 20L * 30);
    }

    @Override
    public void register(Plugin owner, String schemaResource, Consumer<Set<String>> onChange) {
        SettingsSchema schema;
        try (InputStream in = owner.getResource(schemaResource)) {
            if (in == null) {
                logger.warning(owner.getName() + " has no settings schema at " + schemaResource);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Object raw = SafeYaml.create().load(reader);
                schema = SettingsSchema.parse(SettingsSchema.map(raw));
            }
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not read the settings schema " + schemaResource + " of "
                    + owner.getName(), e);
            return;
        }
        File file = new File(owner.getDataFolder(), schema.file());
        if (!file.exists() && owner.getResource(schema.file()) != null) {
            owner.saveResource(schema.file(), false);
        }
        local.put(schema.id(), new Local(owner, schema, file, onChange));
        schedulePublish();
    }

    // ------------------------------------------------------------------ reading

    public Map<String, FileView> views() {
        return views(null);
    }

    /** Every server group that reported settings, this server's included. */
    public Set<String> groups() {
        Set<String> out = new java.util.TreeSet<>();
        out.add(group.get());
        long now = System.currentTimeMillis();
        for (RemoteServer r : remote.values()) {
            if (r.files.values().stream().anyMatch(f -> now - f.at <= REMOTE_TTL_MS)) {
                out.add(r.group);
            }
        }
        return out;
    }

    /** @param onlyGroup only servers of this group, or every server when {@code null} */
    public Map<String, FileView> views(@Nullable String onlyGroup) {
        Map<String, FileView> views = new LinkedHashMap<>();
        String self = coordination.serverId();
        boolean includeLocal = onlyGroup == null || onlyGroup.equals(group.get());
        for (Local l : includeLocal ? local.values() : List.<Local>of()) {
            List<String> hosts = new ArrayList<>(List.of(self));
            views.put(l.schema.id(), new FileView(l.schema, l.owner.getName(), hosts, valuesOf(l), new LinkedHashSet<>()));
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, RemoteServer> server : remote.entrySet()) {
            if (onlyGroup != null && !onlyGroup.equals(server.getValue().group)) {
                continue;
            }
            for (Map.Entry<String, Remote> e : server.getValue().files.entrySet()) {
                Remote r = e.getValue();
                if (now - r.at > REMOTE_TTL_MS) {
                    continue;
                }
                FileView view = views.get(e.getKey());
                if (view == null) {
                    views.put(e.getKey(), new FileView(r.schema, r.plugin, new ArrayList<>(List.of(server.getKey())),
                            r.values, new LinkedHashSet<>()));
                    continue;
                }
                view.hosts().add(server.getKey());
                for (Field f : view.schema().fields()) {
                    if (!SettingsValues.same(view.values().get(f.path()), r.values.get(f.path()))) {
                        view.divergent().add(f.path());
                    }
                }
            }
        }
        List<FileView> sorted = new ArrayList<>(views.values());
        sorted.sort(Comparator.comparingInt((FileView v) -> v.schema().id().equals("teacore") ? 0
                : v.schema().id().startsWith("teacore") ? 1 : 2).thenComparing(v -> v.schema().title()));
        Map<String, FileView> out = new LinkedHashMap<>();
        for (FileView v : sorted) {
            out.put(v.schema().id(), v);
        }
        return out;
    }

    public @Nullable FileView view(String fileId) {
        return views().get(fileId);
    }

    public @Nullable FileView view(String fileId, @Nullable String onlyGroup) {
        return views(onlyGroup).get(fileId);
    }

    private Map<String, Object> valuesOf(Local l) {
        Object doc;
        try {
            doc = YamlPatcher.load(read(l.file));
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not read " + l.file, e);
            doc = Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field f : l.schema.fields()) {
            Object v = SettingsValues.deepCopy(SettingsValues.get(doc, f.path()));
            if (f.type() == Type.LIST && v instanceof List<?> items) {
                List<Object> normalized = new ArrayList<>();
                for (Object item : items) {
                    normalized.add(item instanceof String s && f.child("material").isPresent()
                            ? new LinkedHashMap<>(Map.of("material", s)) : item);
                }
                v = normalized;
            }
            values.put(f.path(), v);
        }
        return values;
    }

    // ----------------------------------------------------------------- changing

    /**
     * Validates the changes and writes them on every server that has the file,
     * or only on {@code server}.
     *
     * @return the servers the change went to
     * @throws IllegalArgumentException with a message for staff when a change doesn't fit the schema
     */
    public List<String> apply(String fileId, List<Change> changes, String actor, @Nullable String onlyGroup) {
        return apply(fileId, changes, actor, onlyGroup, null);
    }

    /**
     * @param onlyGroup apply to and store for this server group only; {@code null} for every server
     * @param reverts   the history entry this change undoes, if any
     */
    public List<String> apply(String fileId, List<Change> changes, String actor, @Nullable String onlyGroup,
                              @Nullable String reverts) {
        FileView view = view(fileId, onlyGroup);
        if (view == null) {
            throw new IllegalArgumentException(onlyGroup == null ? "Unknown settings file " + fileId
                    : "No " + onlyGroup + " server has " + fileId);
        }
        List<Change> writes = toWrites(view, changes);
        if (writes.isEmpty()) {
            return List.of();
        }
        Map<String, Object> doc = view.document();
        Map<String, Object> before = new LinkedHashMap<>();
        for (Change w : writes) {
            before.put(w.path(), SettingsValues.deepCopy(SettingsValues.get(doc, w.path())));
        }
        SettingsStore s = store;
        if (s != null) {
            String scope = onlyGroup == null ? "" : onlyGroup;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> s.record(fileId, scope, view.plugin(),
                    view.schema().file(), writes, before, actor, reverts));
        }
        List<String> targets = List.copyOf(view.hosts());
        String self = coordination.serverId();
        String payload;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("file", fileId);
            body.put("actor", actor);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Change w : writes) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("path", w.path());
                m.put("value", w.value());
                list.add(m);
            }
            body.put("changes", list);
            payload = json.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (String target : targets) {
            if (target.equals(self)) {
                applyLocal(fileId, writes, actor);
            } else {
                coordination.publishControl(target, new ControlCommand(ControlCommand.Type.APPLY_SETTINGS,
                        null, null, null, null, null, null, payload));
            }
        }
        return targets;
    }

    public @Nullable SettingsStore store() {
        return store;
    }

    /**
     * Puts back the value from before a history entry, for the same servers.
     *
     * @throws IllegalArgumentException when there is nothing to go back to
     */
    public List<String> revert(String historyId, String actor) {
        SettingsStore s = store;
        SettingsStore.Entry e = s == null ? null : s.entry(historyId).orElse(null);
        if (e == null) {
            throw new IllegalArgumentException("Unknown change " + historyId);
        }
        if (e.revertedBy() != null) {
            throw new IllegalArgumentException("That change was already undone");
        }
        FileView v = view(e.fileId(), e.scope().isEmpty() ? null : e.scope());
        if (e.oldValue() == null && v != null && v.schema().resolve(e.path())
                .map(r -> r.kind() == SettingsSchema.Kind.VALUE).orElse(true)) {
            throw new IllegalArgumentException("That setting had no value before, so there is nothing to go back to");
        }
        return apply(e.fileId(), List.of(new Change(e.path(), e.oldValue())), actor,
                e.scope().isEmpty() ? null : e.scope(), historyId);
    }

    /** Runs a change another server validated. */
    public void applyRemote(String payload) {
        try {
            Map<String, Object> body = json.readValue(payload, new TypeReference<Map<String, Object>>() { });
            List<Change> writes = new ArrayList<>();
            for (Object o : SettingsSchema.list(body.get("changes"))) {
                Map<String, Object> m = SettingsSchema.map(o);
                writes.add(new Change(String.valueOf(m.get("path")), m.get("value")));
            }
            applyLocal(String.valueOf(body.get("file")), writes, String.valueOf(body.get("actor")));
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not apply settings from another server", e);
        }
    }

    /**
     * Turns changes at any depth into writes the patcher can do: plain values and
     * whole map entries at their path, anything inside a list as the whole list.
     */
    static List<Change> toWrites(FileView view, List<Change> changes) {
        Map<String, Object> doc = view.document();
        Set<String> paths = new LinkedHashSet<>();
        for (Change c : changes) {
            Resolved r = view.schema().resolve(c.path()).orElseThrow(
                    () -> new IllegalArgumentException("Unknown setting " + c.path()));
            Object v = c.value();
            switch (r.kind()) {
                case VALUE -> {
                    if (v == null) {
                        throw new IllegalArgumentException(r.field().title() + " needs a value");
                    }
                    v = SettingsSchema.validate(r.field(), v);
                }
                case ENTRY, ITEM -> v = v == null ? null : SettingsSchema.validateEntry(r.field(), v);
            }
            SettingsValues.setAt(doc, c.path(), v);
            String path = r.listPath() != null ? r.listPath() : c.path();
            boolean covered = paths.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "."));
            if (!covered) {
                paths.removeIf(p -> p.startsWith(path + "."));
                paths.add(path);
            }
        }
        List<Change> writes = new ArrayList<>();
        for (String p : paths) {
            writes.add(new Change(p, SettingsValues.deepCopy(SettingsValues.get(doc, p))));
        }
        return writes;
    }

    private synchronized void applyLocal(String fileId, List<Change> writes, String actor) {
        Local l = local.get(fileId);
        if (l == null) {
            logger.warning("Ignored settings for unknown file " + fileId + " from " + actor);
            return;
        }
        try {
            writes = toWrites(new FileView(l.schema, l.owner.getName(), List.of(coordination.serverId()),
                    valuesOf(l), new LinkedHashSet<>()), writes);
        } catch (RuntimeException e) {
            logger.warning("Rejected settings for " + fileId + " from " + actor + ": " + e.getMessage());
            return;
        }
        if (writes.isEmpty()) {
            return;
        }
        try {
            String original = l.file.exists() ? read(l.file) : "";
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = SettingsSchema.copy(
                    (Map<?, ?>) SettingsValues.deepCopy(YamlPatcher.load(original)));
            String text = original;
            for (Change w : writes) {
                text = YamlPatcher.set(text, w.path(), w.value());
                SettingsValues.put(expected, w.path(), w.value());
            }
            if (!SettingsValues.same(YamlPatcher.load(text), expected)) {
                // Rewrite the affected top-level sections whole; this loses comments inside them only.
                text = original;
                Set<String> tops = new LinkedHashSet<>();
                for (Change w : writes) {
                    tops.add(w.path().split("\\.")[0]);
                }
                for (String top : tops) {
                    text = YamlPatcher.set(text, top, expected.get(top));
                }
                if (!SettingsValues.same(YamlPatcher.load(text), expected)) {
                    throw new IllegalStateException("the edited file didn't read back as expected");
                }
                logger.warning("Rewrote " + tops + " in " + l.file.getName()
                        + " in full; comments inside those sections were lost.");
            }
            write(l.file.toPath(), text);
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not save settings to " + l.file, e);
            return;
        }
        Set<String> paths = new LinkedHashSet<>();
        for (Change w : writes) {
            paths.add(w.path());
        }
        logger.info(actor + " changed " + fileId + ": " + String.join(", ", paths));
        runOnMain(() -> {
            try {
                l.onChange.accept(paths);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, l.owner.getName() + " failed to apply its new settings", e);
            }
        });
        schedulePublish();
    }

    // ------------------------------------------------------------------ syncing

    private void schedulePublish() {
        if (publishPending || !plugin.isEnabled()) {
            return;
        }
        publishPending = true;
        Bukkit.getScheduler().runTaskLater(plugin, this::publish, 10L);
    }

    private void publish() {
        publishPending = false;
        if (local.isEmpty()) {
            return;
        }
        List<Map<String, Object>> files = new ArrayList<>();
        for (Local l : local.values()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("schema", l.schema.source());
            f.put("plugin", l.owner.getName());
            f.put("values", valuesOf(l));
            files.add(f);
        }
        try {
            String payload = json.writeValueAsString(files);
            coordination.publishEvent(new CoordinationEvent(CoordinationEvent.Type.SETTINGS,
                    coordination.serverId(), null, null, null, null, null, null, null, null, null, 0, -1,
                    Map.of("files", payload, "group", group.get())));
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Could not publish settings", e);
        }
    }

    private void onEvent(CoordinationEvent e) {
        if (e.type() != CoordinationEvent.Type.SETTINGS || e.serverId().equals(coordination.serverId())
                || e.data() == null || e.data().get("files") == null) {
            return;
        }
        try {
            List<Map<String, Object>> files = json.readValue(e.data().get("files"),
                    new TypeReference<List<Map<String, Object>>>() { });
            Map<String, Remote> byId = new ConcurrentHashMap<>();
            String serverGroup = e.data().getOrDefault("group", "arena");
            long now = System.currentTimeMillis();
            for (Map<String, Object> f : files) {
                SettingsSchema schema = SettingsSchema.parse(SettingsSchema.map(f.get("schema")));
                byId.put(schema.id(), new Remote(schema, String.valueOf(f.get("plugin")),
                        SettingsSchema.map(f.get("values")), now));
            }
            remote.put(e.serverId(), new RemoteServer(serverGroup, byId));
        } catch (IOException | RuntimeException ex) {
            logger.log(Level.FINE, "Ignoring settings from " + e.serverId(), ex);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(Path target, String text) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
