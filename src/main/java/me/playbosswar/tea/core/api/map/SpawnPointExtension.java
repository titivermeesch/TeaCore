package me.playbosswar.tea.core.api.map;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Ready-made {@link MapExtension} for a single named {@link SpawnPoint}
 * (ceremony camera, boss pad, respawn room, ...).
 *
 * <p>
 * The subcommand stores the player's current location, including yaw and
 * pitch. Stored coordinates are relative to the map's min corner. Register one
 * per spawn:
 *
 * <pre>{@code
 * public static final MapExtensionKey<SpawnPoint> EVOLUTION_VIEWER =
 *         MapExtensionKey.of("ascend:evolution-viewer", SpawnPoint.class);
 *
 * core.maps().extensions().register(
 *         SpawnPointExtension.builder(EVOLUTION_VIEWER)
 *                 .subcommand("evolutionviewer")
 *                 .required(true)
 *                 .statusLabel("evolution-viewer")
 *                 .build());
 * }</pre>
 */
public final class SpawnPointExtension implements MapExtension<SpawnPoint> {

    private final MapExtensionKey<SpawnPoint> key;
    private final String subcommand;
    private final boolean required;
    private final String statusLabel;
    private final Material icon;
    private final Color color;

    private SpawnPointExtension(Builder b) {
        this.key = b.key;
        this.subcommand = b.subcommand;
        this.required = b.required;
        this.statusLabel = b.statusLabel == null ? b.key.id() : b.statusLabel;
        this.icon = b.icon == null ? Material.ARMOR_STAND : b.icon;
        this.color = b.color == null ? Color.AQUA : b.color;
    }

    public static Builder builder(MapExtensionKey<SpawnPoint> key) {
        return new Builder(key);
    }

    @Override
    public MapExtensionKey<SpawnPoint> key() {
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
        return statusLabel;
    }

    @Override
    public void handleCommand(Player player, MapDraftHandle<SpawnPoint> handle, String[] args) {
        Location loc = player.getLocation();
        handle.set(new SpawnPoint(key.id(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()));
    }

    @Override
    public SpawnPoint fromYaml(ConfigurationSection source) {
        return new SpawnPoint(
                source.getString("id", key.id()),
                source.getDouble("x"),
                source.getDouble("y"),
                source.getDouble("z"),
                (float) source.getDouble("yaw"),
                (float) source.getDouble("pitch"));
    }

    @Override
    public void toYaml(SpawnPoint value, ConfigurationSection target) {
        target.set("id", value.id());
        target.set("x", value.x());
        target.set("y", value.y());
        target.set("z", value.z());
        target.set("yaw", value.yaw());
        target.set("pitch", value.pitch());
    }

    @Override
    public SpawnPoint toRelative(SpawnPoint absolute, int minX, int minY, int minZ) {
        return new SpawnPoint(absolute.id(),
                absolute.x() - minX, absolute.y() - minY, absolute.z() - minZ,
                absolute.yaw(), absolute.pitch());
    }

    @Override
    public SpawnPoint toAbsolute(SpawnPoint relative, int originX, int originY, int originZ) {
        return new SpawnPoint(relative.id(),
                relative.x() + originX, relative.y() + originY, relative.z() + originZ,
                relative.yaw(), relative.pitch());
    }

    @Override
    public List<SpawnPoint> points(SpawnPoint value) {
        return List.of(value);
    }

    @Override
    public SpawnPoint removePoint(SpawnPoint value, int index) {
        return null;
    }

    @Override
    public SpawnPoint placePoint(SpawnPoint current, SpawnPoint point) {
        return new SpawnPoint(key.id(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    @Override
    public Material editorIcon() {
        return icon;
    }

    @Override
    public Color markerColor() {
        return color;
    }

    @Override
    public String describeValue(SpawnPoint value) {
        return String.format("%.2f/%.2f/%.2f", value.x(), value.y(), value.z());
    }

    public static final class Builder {
        private final MapExtensionKey<SpawnPoint> key;
        private String subcommand;
        private boolean required;
        private String statusLabel;
        private Material icon;
        private Color color;

        private Builder(MapExtensionKey<SpawnPoint> key) {
            this.key = key;
        }

        /** The {@code /tea map} subcommand token. Required. */
        public Builder subcommand(String s) {
            this.subcommand = s;
            return this;
        }

        /** If true, the map cannot be saved until this spawn is set. */
        public Builder required(boolean v) {
            this.required = v;
            return this;
        }

        /** Label shown in {@code /tea map status}. Defaults to the key id. */
        public Builder statusLabel(String s) {
            this.statusLabel = s;
            return this;
        }

        /** Item shown for this point in the map editor. Default {@code ARMOR_STAND}. */
        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        /** Particle colour of this point's marker. Default aqua. */
        public Builder markerColor(Color color) {
            this.color = color;
            return this;
        }

        /** @throws IllegalStateException if {@code subcommand} was not set */
        public SpawnPointExtension build() {
            if (subcommand == null) {
                throw new IllegalStateException("SpawnPointExtension.subcommand is required");
            }
            return new SpawnPointExtension(this);
        }
    }
}
