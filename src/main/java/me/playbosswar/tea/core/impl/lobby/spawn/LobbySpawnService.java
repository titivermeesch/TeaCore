package me.playbosswar.tea.core.impl.lobby.spawn;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.ArenaPlayerLeaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/** Off with {@code lobby.manage-spawn: false} so another lobby plugin can own spawning. */
public final class LobbySpawnService implements Listener {

    private final JavaPlugin plugin;
    private final TeaCoreAPI core;

    private boolean manageSpawn;
    private GameMode gameMode;
    private String spawnWorld;
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;

    public LobbySpawnService(JavaPlugin plugin, TeaCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        this.manageSpawn = cfg.getBoolean("lobby.manage-spawn", true);
        String mode = cfg.getString("lobby.gamemode", "ADVENTURE");
        try {
            this.gameMode = GameMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid lobby.gamemode '" + mode + "', defaulting to ADVENTURE");
            this.gameMode = GameMode.ADVENTURE;
        }
        this.spawnWorld = cfg.getString("lobby.spawn.world", "");
        this.spawnX = cfg.getDouble("lobby.spawn.x", 0.5);
        this.spawnY = cfg.getDouble("lobby.spawn.y", 65.0);
        this.spawnZ = cfg.getDouble("lobby.spawn.z", 0.5);
        this.spawnYaw = (float) cfg.getDouble("lobby.spawn.yaw", 0.0);
        this.spawnPitch = (float) cfg.getDouble("lobby.spawn.pitch", 0.0);
    }

    public boolean manageSpawn() {
        return manageSpawn;
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public @Nullable Location spawnLocation() {
        World world = resolveWorld();
        if (world == null)
            return null;
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    public boolean applyTo(Player player) {
        if (!manageSpawn)
            return false;
        Location loc = spawnLocation();
        if (loc == null)
            return false;
        player.teleport(loc);
        player.setGameMode(gameMode);
        return true;
    }

    public void setSpawn(Player player) {
        Location loc = player.getLocation();
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("lobby.spawn.world", loc.getWorld().getName());
        cfg.set("lobby.spawn.x", loc.getX());
        cfg.set("lobby.spawn.y", loc.getY());
        cfg.set("lobby.spawn.z", loc.getZ());
        cfg.set("lobby.spawn.yaw", (double) loc.getYaw());
        cfg.set("lobby.spawn.pitch", (double) loc.getPitch());
        save(cfg);
        reload();
    }

    public void setManageSpawn(boolean enabled) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("lobby.manage-spawn", enabled);
        save(cfg);
        reload();
    }

    public void setGameMode(GameMode mode) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("lobby.gamemode", mode.name());
        save(cfg);
        reload();
    }

    private void save(FileConfiguration cfg) {
        try {
            cfg.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save lobby config", e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!manageSpawn)
            return;
        // Players who logged out inside an arena world are handled by the arena
        // manager's reconnect logic. Only teleport fresh arrivals.
        Player p = event.getPlayer();
        if (p.getWorld().getName().startsWith("tea_arena_"))
            return;
        applyTo(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!manageSpawn)
            return;
        Location loc = spawnLocation();
        if (loc == null)
            return;
        event.setRespawnLocation(loc);
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().setGameMode(gameMode));
    }

    /**
     * Runs next tick so it wins over the core's teleport. In cross-server mode the
     * player is already being sent away, so this is a no-op.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaLeave(ArenaPlayerLeaveEvent event) {
        if (!manageSpawn)
            return;
        Player p = event.player();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline())
                return;
            if (p.getWorld().getName().startsWith("tea_arena_"))
                return;
            applyTo(p);
        });
    }

    private @Nullable World resolveWorld() {
        if (spawnWorld != null && !spawnWorld.isBlank()) {
            World w = Bukkit.getWorld(spawnWorld);
            if (w != null)
                return w;
        }
        return Bukkit.getWorlds().stream()
                .filter(w -> !w.getName().startsWith("tea_arena_"))
                .findFirst()
                .orElse(null);
    }
}
