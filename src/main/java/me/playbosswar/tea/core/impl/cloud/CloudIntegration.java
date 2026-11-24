package me.playbosswar.tea.core.impl.cloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import me.playbosswar.tea.core.impl.arena.ArenaManagerImpl;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

public final class CloudIntegration {

    private static volatile CloudIntegration instance;

    private final Plugin plugin;
    private final Logger logger;
    private final CloudPlatform cloud;
    private final ArenaManagerImpl arenas;
    private final CoordinationBackend coordination;
    private final Map<String, Long> lastStart = new HashMap<>();
    private final Map<String, Long> emptySince = new HashMap<>();
    private BukkitTask scaleTask;
    private BukkitTask stateTask;
    private String lastState = "";

    public CloudIntegration(Plugin plugin, CloudPlatform cloud, ArenaManagerImpl arenas,
                            CoordinationBackend coordination) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.cloud = cloud;
        this.arenas = arenas;
        this.coordination = coordination;
        instance = this;
    }

    public static @Nullable CloudIntegration get() {
        return instance;
    }

    public CloudPlatform cloud() {
        return cloud;
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("cloudnet");
        return s != null ? s : plugin.getConfig().createSection("cloudnet");
    }

    public void start() {
        logger.info("Running in " + cloud.name() + " as " + cloud.serviceName() + " (task " + cloud.taskName() + ").");
        scaleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scaleTick, 100L, 100L);
        if (cfg().getBoolean("publish-state", true)) {
            stateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::publishState, 40L, 40L);
        }
    }

    public void stop() {
        if (scaleTask != null) scaleTask.cancel();
        if (stateTask != null) stateTask.cancel();
        instance = null;
    }

    /** The configured lobby name means "any lobby service". */
    public ProxyTransport transport(String lobbyServerName) {
        String lobbyTask = cfg().getString("lobby-task", "Lobby");
        return (Player player, String target) -> {
            try {
                if (target.equalsIgnoreCase(lobbyServerName) || target.equalsIgnoreCase(lobbyTask)) {
                    cloud.connectToTask(player, lobbyTask);
                } else {
                    cloud.connect(player, target);
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not send " + player.getName() + " to " + target + " through "
                        + cloud.name(), e);
            }
        };
    }

    private record GameCloud(String task, int minServices, int maxServices, int arenasPerService, int idleSeconds) {
    }

    private @Nullable GameCloud gameCloud(String game) {
        ConfigurationSection g = cfg().getConfigurationSection("games." + game);
        if (g == null || g.getString("task") == null) {
            return null;
        }
        return new GameCloud(g.getString("task"), Math.max(0, g.getInt("min-services", 1)),
                Math.max(1, g.getInt("max-services", 4)), Math.max(1, g.getInt("arenas-per-service", 4)),
                Math.max(10, g.getInt("idle-seconds", 120)));
    }

    public int arenasPerService(GameId game) {
        GameCloud gc = gameCloud(game.value());
        return gc == null ? Integer.MAX_VALUE : gc.arenasPerService();
    }

    private void scaleTick() {
        ConfigurationSection games = cfg().getConfigurationSection("games");
        if (games == null || !coordination.tryAcquireMatchmakerLock()) {
            return;
        }
        for (String game : games.getKeys(false)) {
            GameCloud gc = gameCloud(game);
            if (gc == null) {
                continue;
            }
            try {
                scale(GameId.of(game), gc);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Cloud scaling for " + game + " failed", e);
            }
        }
    }

    private void scale(GameId game, GameCloud gc) {
        List<CloudPlatform.Service> services = cloud.services(gc.task());
        long now = System.currentTimeMillis();
        boolean starting = services.stream().anyMatch(s -> !s.running())
                || now - lastStart.getOrDefault(gc.task(), 0L) < 30_000;
        List<ArenaSnapshot> snaps = coordination.allArenas().stream()
                .filter(s -> s.gameId().equals(game) && s.state() != ArenaState.RELEASED).toList();
        int queued = coordination.queueFor(game).size();
        int freeSlots = snaps.stream().filter(CloudIntegration::joinable)
                .mapToInt(s -> Math.max(0, s.maxPlayers() - s.players())).sum();
        boolean hostsFull = services.stream().filter(CloudPlatform.Service::running)
                .allMatch(s -> snaps.stream().filter(a -> a.serverId().equals(s.name())).count() >= gc.arenasPerService());

        if (!starting && services.size() < gc.minServices()) {
            start(gc, "keeping " + gc.minServices() + " running");
            return;
        }
        if (!starting && queued > freeSlots && hostsFull && services.size() < gc.maxServices()) {
            start(gc, queued + " queued, " + freeSlots + " free slots");
            return;
        }
        if (queued > 0 || services.size() <= gc.minServices()) {
            services.forEach(s -> emptySince.remove(s.name()));
            return;
        }
        for (CloudPlatform.Service s : services) {
            boolean busy = !s.running() || s.players() > 0
                    || snaps.stream().anyMatch(a -> a.serverId().equals(s.name()) && a.players() > 0);
            if (busy) {
                emptySince.remove(s.name());
                continue;
            }
            long since = emptySince.computeIfAbsent(s.name(), k -> now);
            if (now - since >= gc.idleSeconds() * 1000L) {
                emptySince.remove(s.name());
                logger.info("Stopping " + s.name() + ": empty for " + gc.idleSeconds() + "s.");
                cloud.stopService(s.name());
                return;
            }
        }
    }

    private void start(GameCloud gc, String why) {
        lastStart.put(gc.task(), System.currentTimeMillis());
        logger.info("Starting a " + gc.task() + " service (" + why + ").");
        cloud.startService(gc.task());
    }

    private static boolean joinable(ArenaSnapshot s) {
        return s.state() == ArenaState.ALLOCATING
                || ((s.state() == ArenaState.WAITING || s.state() == ArenaState.COUNTDOWN)
                        && (s.maxPlayers() <= 0 || s.players() < s.maxPlayers()));
    }

    private void publishState() {
        List<Arena> local = List.copyOf(arenas.all());
        if (local.isEmpty()) {
            return; // lobby servers keep the cloud's defaults
        }
        Arena open = local.stream()
                .filter(a -> (a.state() == ArenaState.WAITING || a.state() == ArenaState.COUNTDOWN)
                        && (a.maxPlayers() <= 0 || a.players().size() < a.maxPlayers()))
                .findFirst().orElse(null);
        boolean ingame = open == null;
        Arena shown = open != null ? open : local.get(0);
        String extra = shown.gameId().value() + ":" + shown.mapId().orElse("");
        String motd = shown.players().size() + "/" + shown.maxPlayers();
        int max = local.stream().mapToInt(Arena::maxPlayers).sum();
        String key = ingame + "|" + extra + "|" + motd + "|" + max;
        if (key.equals(lastState)) {
            return;
        }
        lastState = key;
        try {
            cloud.publishState(ingame, extra, motd, max);
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not publish cloud state", e);
        }
    }

    public static void mapsChanged() {
        CloudIntegration ci = instance;
        if (ci != null) {
            ci.syncTemplates();
        }
    }

    private void syncTemplates() {
        if (!cfg().getBoolean("template-sync.enabled", false)) {
            return;
        }
        List<String> templates = cfg().getStringList("template-sync.templates");
        if (templates.isEmpty()) {
            logger.warning("cloudnet.template-sync is on but lists no templates.");
            return;
        }
        Path data = plugin.getDataFolder().toPath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path tmp = null;
            try {
                tmp = Files.createTempDirectory("tea-template-sync");
                Path target = tmp.resolve("plugins").resolve("TeaCore");
                for (String folder : List.of("maps", "waiting-rooms")) {
                    copyTree(data.resolve(folder), target.resolve(folder));
                }
                for (String template : templates) {
                    boolean ok = cloud.deploy(tmp, template);
                    logger.info((ok ? "Uploaded" : "Could not upload") + " maps and waiting rooms to template " + template);
                }
            } catch (IOException | RuntimeException e) {
                logger.log(Level.WARNING, "Template sync failed", e);
            } finally {
                if (tmp != null) {
                    deleteTree(tmp);
                }
            }
        });
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp folder, the OS cleans it up eventually
        }
    }
}
