package me.playbosswar.tea.core.api.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Ready-made {@link MapExtension} for a <em>list</em> of points (chests,
 * cannons, power-up pads, ...).
 *
 * <p>
 * {@code /tea map <subcommand>} adds the author's current block position;
 * {@code /tea map <subcommand> clear} removes them all. The map editor's point
 * tool places points on the clicked block and removes the marker you punch.
 * Points are saved under {@code extensions.<id>.points} relative to the region
 * min-corner and read back with
 * {@link MapDefinition#extension(MapExtensionKey)}.
 *
 * <pre>{@code
 * MapExtensionKey<List<SpawnPoint>> CHESTS = MultiPointExtension.key("mygame:chests");
 * core.maps().extensions().register(MultiPointExtension.builder(CHESTS)
 *         .subcommand("chest").label("chest").icon(Material.CHEST).build());
 * }</pre>
 */
public final class MultiPointExtension implements MapExtension<List<SpawnPoint>> {

    private final MapExtensionKey<List<SpawnPoint>> key;
    private final String subcommand;
    private final String label;
    private final boolean required;
    private final Material icon;
    private final Color color;
    private final boolean onClickedBlock;

    private MultiPointExtension(Builder b) {
        this.key = b.key;
        this.subcommand = b.subcommand;
        this.label = b.label == null ? b.subcommand : b.label;
        this.required = b.required;
        this.icon = b.icon;
        this.color = b.color == null ? Color.YELLOW : b.color;
        this.onClickedBlock = b.onClickedBlock;
    }

    /** Builds a {@code List<SpawnPoint>}-typed key for the given id. */
    @SuppressWarnings("unchecked")
    public static MapExtensionKey<List<SpawnPoint>> key(String id) {
        return MapExtensionKey.of(id, (Class<List<SpawnPoint>>) (Class<?>) List.class);
    }

    public static Builder builder(MapExtensionKey<List<SpawnPoint>> key) {
        return new Builder(key);
    }

    @Override
    public MapExtensionKey<List<SpawnPoint>> key() {
        return key;
    }

    @Override
    public String subcommand() {
        return subcommand;
    }

    @Override
    public boolean required() {
        return required;
    }

    @Override
    public String statusLabel() {
        return label;
    }

    @Override
    public void handleCommand(Player player, MapDraftHandle<List<SpawnPoint>> handle, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            handle.clear();
            return;
        }
        Location loc = player.getLocation();
        handle.set(placePoint(handle.current().orElse(null), new SpawnPoint(label,
                loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5,
                loc.getYaw(), loc.getPitch())));
    }

    @Override
    public List<SpawnPoint> points(List<SpawnPoint> value) {
        return value;
    }

    @Override
    public List<SpawnPoint> removePoint(List<SpawnPoint> value, int index) {
        if (index < 0 || index >= value.size()) {
            return value;
        }
        List<SpawnPoint> out = new ArrayList<>(value);
        out.remove(index);
        return out.isEmpty() ? null : renumber(out);
    }

    @Override
    public List<SpawnPoint> placePoint(List<SpawnPoint> current, SpawnPoint point) {
        List<SpawnPoint> out = new ArrayList<>(current == null ? List.of() : current);
        out.add(point);
        return renumber(out);
    }

    /** Point ids are {@code <label>-<index>}, kept in order after removals. */
    private List<SpawnPoint> renumber(List<SpawnPoint> points) {
        List<SpawnPoint> out = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            SpawnPoint p = points.get(i);
            out.add(new SpawnPoint(label + "-" + i, p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
        }
        return out;
    }

    @Override
    public boolean pointOnClickedBlock() {
        return onClickedBlock;
    }

    @Override
    public Material editorIcon() {
        // TARGET only exists on 1.16+; the platform picks something close on older versions.
        return icon != null ? icon : me.playbosswar.tea.core.api.TeaCoreAPI.get().platform().items()
                .material("TARGET").orElse(Material.BEACON);
    }

    @Override
    public Color markerColor() {
        return color;
    }

    @Override
    public List<SpawnPoint> fromYaml(ConfigurationSection source) {
        List<SpawnPoint> points = new ArrayList<>();
        for (Map<?, ?> raw : source.getMapList("points")) {
            Object id = raw.get("id");
            points.add(new SpawnPoint(
                    id == null ? "p" : String.valueOf(id),
                    toDouble(raw.get("x")), toDouble(raw.get("y")), toDouble(raw.get("z")),
                    (float) toDouble(raw.get("yaw")), (float) toDouble(raw.get("pitch"))));
        }
        return points;
    }

    @Override
    public void toYaml(List<SpawnPoint> value, ConfigurationSection target) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SpawnPoint point : value) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", point.id());
            entry.put("x", point.x());
            entry.put("y", point.y());
            entry.put("z", point.z());
            entry.put("yaw", point.yaw());
            entry.put("pitch", point.pitch());
            out.add(entry);
        }
        target.set("points", out);
    }

    @Override
    public List<SpawnPoint> toRelative(List<SpawnPoint> absolute, int minX, int minY, int minZ) {
        List<SpawnPoint> out = new ArrayList<>(absolute.size());
        for (SpawnPoint p : absolute) {
            out.add(new SpawnPoint(p.id(), p.x() - minX, p.y() - minY, p.z() - minZ, p.yaw(), p.pitch()));
        }
        return out;
    }

    @Override
    public List<SpawnPoint> toAbsolute(List<SpawnPoint> relative, int originX, int originY, int originZ) {
        List<SpawnPoint> out = new ArrayList<>(relative.size());
        for (SpawnPoint p : relative) {
            out.add(new SpawnPoint(p.id(), p.x() + originX, p.y() + originY, p.z() + originZ, p.yaw(), p.pitch()));
        }
        return out;
    }

    @Override
    public String describeValue(List<SpawnPoint> value) {
        return value.size() + " point(s)";
    }

    private static double toDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    public static final class Builder {
        private final MapExtensionKey<List<SpawnPoint>> key;
        private String subcommand;
        private String label;
        private boolean required;
        private Material icon;
        private Color color;
        private boolean onClickedBlock;

        private Builder(MapExtensionKey<List<SpawnPoint>> key) {
            this.key = key;
        }

        /** The {@code /tea map <subcommand>} token. Required. */
        public Builder subcommand(String subcommand) {
            this.subcommand = subcommand;
            return this;
        }

        /** Label for status lines, markers and point ids. Defaults to the subcommand. */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /** Whether a map needs at least one point before it can be saved. Default false. */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /** Item shown for these points in the map editor. Default {@code TARGET}. */
        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        /** Particle colour of these markers. Default yellow. */
        public Builder markerColor(Color color) {
            this.color = color;
            return this;
        }

        /**
         * The editor's point tool marks the clicked block itself instead of the
         * space on top of it. Use it for points that are blocks (chests, cannons).
         */
        public Builder onClickedBlock() {
            this.onClickedBlock = true;
            return this;
        }

        public MultiPointExtension build() {
            if (subcommand == null) {
                throw new IllegalStateException("MultiPointExtension.subcommand is required");
            }
            return new MultiPointExtension(this);
        }
    }
}
