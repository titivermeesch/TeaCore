package me.playbosswar.tea.core.impl.arena;

import java.util.Optional;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/** Shared waiting areas outside the arena, in any world. Players move into the arena when the countdown ends. */
public final class WaitingAreas {

    private final Plugin plugin;

    public WaitingAreas(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The waiting area for this game: its own, else the default one. Empty if none or its world isn't loaded. */
    public Optional<Location> forGame(GameId game) {
        Optional<Location> own = read("waiting-area." + game.value());
        return own.isPresent() ? own : read("waiting-area.default");
    }

    private Optional<Location> read(String path) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(path);
        if (s == null || s.getString("world") == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(s.getString("world"));
        if (world == null) {
            plugin.getLogger().warning(path + ": world '" + s.getString("world") + "' is not loaded; "
                    + "players wait inside the arena instead.");
            return Optional.empty();
        }
        return Optional.of(new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
    }

    public void set(String key, Location at) {
        String path = "waiting-area." + key;
        plugin.getConfig().set(path + ".world", at.getWorld().getName());
        plugin.getConfig().set(path + ".x", round(at.getX()));
        plugin.getConfig().set(path + ".y", round(at.getY()));
        plugin.getConfig().set(path + ".z", round(at.getZ()));
        plugin.getConfig().set(path + ".yaw", round(at.getYaw()));
        plugin.getConfig().set(path + ".pitch", round(at.getPitch()));
        plugin.saveConfig();
    }

    public boolean clear(String key) {
        String path = "waiting-area." + key;
        if (plugin.getConfig().get(path) == null) {
            return false;
        }
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        return true;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
