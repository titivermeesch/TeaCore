package me.playbosswar.tea.core.api.map;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Game-owned data attached to a {@link MapDefinition}. Register it on enable
 * with {@link MapExtensionRegistry#register(MapExtension)}.
 *
 * <p>
 * TeaCore routes {@code /tea map <subcommand>} to {@link #handleCommand},
 * stores the value under {@code extensions.<id>} in the map YAML via
 * {@link #toYaml}/{@link #fromYaml}, and game code reads it back with
 * {@link MapDefinition#extension(MapExtensionKey)}.
 *
 * <p>
 * Extensions holding coordinates should override {@link #toRelative} and
 * {@link #toAbsolute}: drafts hold absolute edit-world positions, saved
 * definitions hold positions relative to the region min-corner. The defaults
 * are identity.
 *
 * @param <T> the extension's value type.
 */
public interface MapExtension<T> {

    MapExtensionKey<T> key();

    /**
     * The token after {@code /tea map} that routes to this extension. Matched
     * case-insensitively; must not collide with a built-in map subcommand.
     */
    String subcommand();

    /**
     * When {@code true}, {@code /tea map save} is blocked until the draft has a
     * value for this extension.
     */
    default boolean required() {
        return false;
    }

    /** Label shown in {@code /tea map status}. Defaults to the extension id. */
    default String statusLabel() {
        return key().id();
    }

    /**
     * Called only when the player has an active draft and is standing in its
     * edit world.
     *
     * @param args the arguments after the subcommand token.
     */
    void handleCommand(Player player, MapDraftHandle<T> handle, String[] args);

    /**
     * @param source the {@code extensions.<id>} section.
     * @return the value, or {@code null} for a malformed entry, which TeaCore
     *         logs and skips.
     */
    T fromYaml(ConfigurationSection source);

    /**
     * @param target an empty section at {@code extensions.<id>}.
     */
    void toYaml(T value, ConfigurationSection target);

    /**
     * Converts a draft value (absolute edit-world coordinates) to a saved value
     * relative to the region min-corner.
     */
    default T toRelative(T absolute, int minX, int minY, int minZ) {
        return absolute;
    }

    /**
     * Converts a saved value back to absolute edit-world coordinates when a map
     * is reopened with {@code /tea map edit}.
     */
    default T toAbsolute(T relative, int originX, int originY, int originZ) {
        return relative;
    }

    /** One-line rendering for {@code /tea map status}. */
    default String describeValue(T value) {
        return String.valueOf(value);
    }

    /**
     * The game this extension belongs to. Map validation, status, the setup
     * sidebar and the editor tools only use extensions that apply to the
     * draft's game.
     *
     * <p>
     * Defaults to the key id prefix before the first {@code ':'}
     * ({@code "ascend:evolution-mob"} belongs to {@code ascend}). Empty means
     * the extension applies to every game.
     */
    default Optional<GameId> game() {
        String id = key().id();
        int colon = id.indexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(GameId.of(id.substring(0, colon).toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** {@code true} when {@link #game()} is empty or equals {@code gameId}. */
    default boolean appliesTo(GameId gameId) {
        return game().map(gameId::equals).orElse(true);
    }

    /**
     * Points shown as particle markers in the map editor, in index order and in
     * the same coordinate space as {@code value}. Defaults to none.
     */
    default List<SpawnPoint> points(T value) {
        return List.of();
    }

    /**
     * Removes the point at {@code index} (as returned by {@link #points}).
     *
     * @return the new value, or {@code null} to clear the extension.
     */
    default @Nullable T removePoint(T value, int index) {
        return null;
    }

    /**
     * Places a point with the editor's point tool. Return {@code null} if
     * placement by position is unsupported; the tool then calls
     * {@link #handleCommand} with no arguments instead.
     *
     * @param current the current value, or {@code null} when unset.
     * @param point   absolute in the edit world.
     */
    default @Nullable T placePoint(@Nullable T current, SpawnPoint point) {
        return null;
    }

    /**
     * {@code false} places a point on top of the right-clicked block (where a
     * player stands); {@code true} places it on the block itself, for points
     * that mark a block such as a chest.
     */
    default boolean pointOnClickedBlock() {
        return false;
    }

    default Material editorIcon() {
        return Material.PAPER;
    }

    default Color markerColor() {
        return Color.WHITE;
    }
}
