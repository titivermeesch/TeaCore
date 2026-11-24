package me.playbosswar.tea.core.api.settings;

import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/**
 * Makes a plugin's YAML files editable from {@code /tea admin} and the web
 * dashboard. A plugin ships a schema in its jar that lists the settings, their
 * types and limits; TeaCore builds the menus and the dashboard form from it,
 * writes changes back into the file (keeping comments outside the edited
 * value) and pushes them to every server of the network that has the same
 * file.
 *
 * <p>Schema format: see the {@code settings/} files in TeaCore's own jar and the
 * developer docs.
 */
public interface SettingsService {

    /**
     * @param schemaResource path of the schema inside {@code owner}'s jar, e.g.
     *                       {@code settings/config.yml}
     * @param onChange       runs on the main thread after a change was written,
     *                       with the changed setting paths. Re-read the file here.
     */
    void register(Plugin owner, String schemaResource, Consumer<Set<String>> onChange);
}
