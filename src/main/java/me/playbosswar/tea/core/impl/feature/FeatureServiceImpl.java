package me.playbosswar.tea.core.impl.feature;

import me.playbosswar.tea.core.api.feature.FeatureService;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.plugin.Plugin;

/** Reads {@code features.*} from TeaCore's live config on every call. */
public final class FeatureServiceImpl implements FeatureService {

    private final Plugin plugin;

    public FeatureServiceImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enabled(String feature) {
        return plugin.getConfig().getBoolean("features." + feature, true);
    }

    @Override
    public boolean enabled(GameId game, String feature) {
        String override = "features.games." + game.value() + "." + feature;
        if (plugin.getConfig().isBoolean(override)) {
            return plugin.getConfig().getBoolean(override);
        }
        return enabled(feature);
    }
}
