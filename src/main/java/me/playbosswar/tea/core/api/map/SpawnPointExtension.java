package me.playbosswar.tea.core.api.map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Ready-made {@link MapExtension} for games whose data is a single named
 * {@link SpawnPoint} (ceremony camera, boss pad, respawn room, ...).
 *
 * <p>
 * {@code handleCommand} captures the player's current location (including yaw
 * and pitch) and stores it on the draft. Persistence writes
 * {@code {x, y, z, yaw, pitch}} keys under the extension's YAML section, and
 * coordinates round-trip correctly between authoring (absolute in edit-world)
 * and stored (relative to the map's min-corner). Games instantiate one per
 * spawn:
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

    private SpawnPointExtension(Builder b) {
        this.key = b.key;
        this.subcommand = b.subcommand;
        this.required = b.required;
        this.statusLabel = b.statusLabel == null ? b.key.id() : b.statusLabel;
    }

    /**
     * Starts building a spawn-point extension for the given key.
     *
     * @param key the typed key; must not be {@code null}.
     * @return a new builder.
     */
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
    public String describeValue(SpawnPoint value) {
        return String.format("%.2f/%.2f/%.2f", value.x(), value.y(), value.z());
    }

    /**
     * Fluent builder for {@link SpawnPointExtension}.
     */
    public static final class Builder {
        private final MapExtensionKey<SpawnPoint> key;
        private String subcommand;
        private boolean required;
        private String statusLabel;

        private Builder(MapExtensionKey<SpawnPoint> key) {
            this.key = key;
        }

        /**
         * Sets the {@code /tea map} subcommand token.
         *
         * @param s subcommand; must not be {@code null}.
         * @return this builder.
         */
        public Builder subcommand(String s) {
            this.subcommand = s;
            return this;
        }

        /**
         * Marks the extension as required for save validation.
         *
         * @param v {@code true} to require the spawn be set before save.
         * @return this builder.
         */
        public Builder required(boolean v) {
            this.required = v;
            return this;
        }

        /**
         * Sets a short label shown in {@code /tea map status} (defaults to the
         * extension id when omitted).
         *
         * @param s status label.
         * @return this builder.
         */
        public Builder statusLabel(String s) {
            this.statusLabel = s;
            return this;
        }

        /**
         * Builds the extension.
         *
         * @return a new {@link SpawnPointExtension}.
         * @throws IllegalStateException if {@code subcommand} has not been set.
         */
        public SpawnPointExtension build() {
            if (subcommand == null) {
                throw new IllegalStateException("SpawnPointExtension.subcommand is required");
            }
            return new SpawnPointExtension(this);
        }
    }
}
