package me.playbosswar.tea.core.impl.platform;

import java.util.logging.Logger;
import me.playbosswar.tea.core.api.platform.Platform;
import me.playbosswar.tea.core.api.platform.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * The modern and legacy implementations are compiled against different server
 * APIs and only loaded here, so the other one's classes are never touched.
 */
public final class Platforms {

    private Platforms() {
    }

    public static Platform load(Plugin plugin) {
        ServerVersion version = ServerVersion.parse(Bukkit.getBukkitVersion());
        String impl = version.legacy()
                ? "me.playbosswar.tea.core.impl.platform.legacy.LegacyPlatform"
                : "me.playbosswar.tea.core.impl.platform.modern.ModernPlatform";
        try {
            Platform platform = (Platform) Class.forName(impl)
                    .getConstructor(Plugin.class, ServerVersion.class)
                    .newInstance(plugin, version);
            Logger log = plugin.getLogger();
            log.info("Platform: " + (version.legacy() ? "legacy" : "modern") + " (Minecraft " + version + ")");
            return platform;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not load the " + impl + " platform", e);
        }
    }
}
