package me.playbosswar.tea.core.api.map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A game-owned extension to the generic {@link MapDefinition} shape.
 *
 * <p>
 * Extensions let games attach arbitrary data to a map without polluting the
 * core model. They are registered on plugin enable via
 * {@link MapExtensionRegistry#register(MapExtension)} and participate in three
 * flows:
 *
 * <ul>
 * <li><b>Authoring.</b> TeaCore dispatches {@code /tea map <subcommand> ...}
 * to {@link #handleCommand} when the admin runs it inside their active draft;
 * the handler reads the player's position/args and mutates the draft through
 * the supplied {@link MapDraftHandle}.</li>
 * <li><b>Persistence.</b> TeaCore calls {@link #toYaml} to serialize the
 * stored value under {@code extensions.<id>} in the map YAML file, and
 * {@link #fromYaml} to reconstruct it on load.</li>
 * <li><b>Lookup.</b> Game code reads the value back with
 * {@link MapDefinition#extension(MapExtensionKey)}.</li>
 * </ul>
 *
 * <p>
 * If the extension carries spatial coordinates that should survive the
 * edit-world to paste-origin relocation, override
 * {@link #toRelative(Object, int, int, int)} and
 * {@link #toAbsolute(Object, int, int, int)}. TeaCore calls them as it moves
 * data between the live edit world (absolute) and the saved definition
 * (relative to the region min-corner). The default implementations are
 * identity; non-spatial extensions can ignore them.
 *
 * @param <T> the extension's value type.
 */
public interface MapExtension<T> {

    /**
     * Returns the typed key used to identify and look up this extension.
     *
     * @return the key, never {@code null}.
     */
    MapExtensionKey<T> key();

    /**
     * Returns the admin subcommand for this extension - the token admins type
     * after {@code /tea map}. For example, returning {@code "evolutionviewer"}
     * wires the extension to {@code /tea map evolutionviewer ...}.
     *
     * <p>
     * Subcommands are matched case-insensitively and must not collide with any
     * built-in TeaCore map subcommand.
     *
     * @return the subcommand token, never {@code null}.
     */
    String subcommand();

    /**
     * Indicates whether a map draft must have a value for this extension to be
     * save-able. When {@code true} the admin sees the missing entry in
     * {@code /tea map status} and cannot {@code /tea map save} until it is
     * filled.
     *
     * @return {@code true} if required; default {@code false}.
     */
    default boolean required() {
        return false;
    }

    /**
     * Returns a short label used in {@code /tea map status} output. Defaults
     * to the extension id.
     *
     * @return a short label for status rendering, never {@code null}.
     */
    default String statusLabel() {
        return key().id();
    }

    /**
     * Handles {@code /tea map <subcommand> [args...]}.
     *
     * <p>
     * Called only when the player has an active draft and is standing in that
     * draft's edit world. Implementations mutate the draft via {@code handle}.
     *
     * @param player player running the command; never {@code null}.
     * @param handle typed handle into the active draft, scoped to this
     *               extension's value; never {@code null}.
     * @param args   raw command args after the subcommand token (so for
     *               {@code /tea map evolutionviewer foo bar} this is
     *               {@code ["foo", "bar"]}); never {@code null}.
     */
    void handleCommand(Player player, MapDraftHandle<T> handle, String[] args);

    /**
     * Reconstructs a value from its YAML representation written earlier by
     * {@link #toYaml}.
     *
     * @param source the YAML subsection at {@code extensions.<id>} (read-only
     *               view for this call); never {@code null}.
     * @return the parsed value; extensions may return {@code null} to indicate
     *         a malformed entry, in which case TeaCore logs and skips.
     */
    T fromYaml(ConfigurationSection source);

    /**
     * Serializes a value into the given YAML subsection.
     *
     * @param value  the value to persist; never {@code null}.
     * @param target an empty configuration section at {@code extensions.<id>};
     *               never {@code null}.
     */
    void toYaml(T value, ConfigurationSection target);

    /**
     * Converts an authoring-time (absolute-in-edit-world) value to a saved
     * (relative-to-min-corner) value. Default: identity.
     *
     * @param absolute value as held on the active draft.
     * @param minX     min-corner X in edit-world blocks.
     * @param minY     min-corner Y in edit-world blocks.
     * @param minZ     min-corner Z in edit-world blocks.
     * @return the value to write into the {@link MapDefinition}.
     */
    default T toRelative(T absolute, int minX, int minY, int minZ) {
        return absolute;
    }

    /**
     * Converts a saved (relative-to-min-corner) value back to an authoring
     * (absolute-in-edit-world) value when the admin reopens a map with
     * {@code /tea map edit}. Default: identity.
     *
     * @param relative stored value.
     * @param originX  edit-world paste-origin X.
     * @param originY  edit-world paste-origin Y.
     * @param originZ  edit-world paste-origin Z.
     * @return the value to load into the draft.
     */
    default T toAbsolute(T relative, int originX, int originY, int originZ) {
        return relative;
    }

    /**
     * Renders a value for the {@code /tea map status} line. Defaults to
     * {@link Object#toString()}.
     *
     * @param value the value to describe; never {@code null}.
     * @return a one-line description.
     */
    default String describeValue(T value) {
        return String.valueOf(value);
    }
}
