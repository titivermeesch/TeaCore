package me.playbosswar.tea.core.impl;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.chat.ChatRouter;
import me.playbosswar.tea.core.api.chat.MatchBroadcaster;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.cooldown.CooldownService;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import me.playbosswar.tea.core.api.effect.EffectService;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.persistence.DatabaseService;
import me.playbosswar.tea.core.api.profile.PlayerProfileService;
import me.playbosswar.tea.core.api.stats.StatsRegistry;
import me.playbosswar.tea.core.api.team.TeamManager;
import me.playbosswar.tea.core.api.theme.Theme;
import me.playbosswar.tea.core.impl.arena.ArenaDisconnectListener;
import me.playbosswar.tea.core.impl.arena.ArenaManagerImpl;
import me.playbosswar.tea.core.impl.arena.ArenaPoolManager;
import me.playbosswar.tea.core.impl.arena.LifecycleTicker;
import me.playbosswar.tea.core.impl.chat.ChatRouterImpl;
import me.playbosswar.tea.core.impl.chat.MatchBroadcasterImpl;
import me.playbosswar.tea.core.impl.command.TeaChatCommand;
import me.playbosswar.tea.core.impl.command.TeaCommand;
import me.playbosswar.tea.core.impl.config.ConfigLoader;
import me.playbosswar.tea.core.impl.config.CoreConfig;
import me.playbosswar.tea.core.impl.coordination.ArenaHeartbeater;
import me.playbosswar.tea.core.impl.coordination.InMemoryBackend;
import me.playbosswar.tea.core.impl.coordination.Matchmaker;
import me.playbosswar.tea.core.impl.coordination.QueueBossbarService;
import me.playbosswar.tea.core.impl.coordination.RedisBackend;
import me.playbosswar.tea.core.impl.dashboard.DashboardServer;
import me.playbosswar.tea.core.impl.transport.BungeeChannelTransport;
import me.playbosswar.tea.core.impl.cooldown.CooldownServiceImpl;
import me.playbosswar.tea.core.impl.effect.EffectServiceImpl;
import me.playbosswar.tea.core.impl.game.GameRegistryImpl;
import me.playbosswar.tea.core.impl.kit.AbilityTriggerListener;
import me.playbosswar.tea.core.impl.kit.KitRegistryImpl;
import me.playbosswar.tea.core.impl.kit.KitSelectorGui;
import me.playbosswar.tea.core.impl.kit.KitSelectorListener;
import me.playbosswar.tea.core.impl.map.MapEditingService;
import me.playbosswar.tea.core.impl.map.MapPersistence;
import me.playbosswar.tea.core.impl.map.MapServiceImpl;
import me.playbosswar.tea.core.impl.message.LocaleBundle;
import me.playbosswar.tea.core.impl.message.LocaleBundleLoader;
import me.playbosswar.tea.core.impl.message.MessageServiceImpl;
import me.playbosswar.tea.core.impl.persistence.DatabaseServiceImpl;
import me.playbosswar.tea.core.impl.stats.StatsRegistryImpl;
import me.playbosswar.tea.core.impl.profile.PlayerProfileServiceImpl;
import me.playbosswar.tea.core.impl.profile.ProfileLifecycleListener;
import me.playbosswar.tea.core.impl.team.TeamManagerImpl;
import me.playbosswar.tea.core.impl.theme.ThemeImpl;
import me.playbosswar.tea.core.impl.theme.ThemeLoader;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeaCorePlugin extends JavaPlugin implements TeaCoreAPI {

    private static final List<String> BUNDLED_LOCALES = List.of("en_US");
    private static final List<String> CORE_MIGRATIONS = List.of("V001__init.sql");

    private CoreConfig config;
    private ThemeImpl theme;
    private BukkitAudiences audiences;
    private MessageServiceImpl messageService;
    private DatabaseServiceImpl databaseService;
    private PlayerProfileServiceImpl playerProfileService;
    private GameRegistryImpl gameRegistry;
    private MapServiceImpl mapService;
    private ArenaManagerImpl arenaManager;
    private LifecycleTicker lifecycleTicker;
    private ArenaPoolManager poolManager;
    private TeamManagerImpl teamManager;
    private KitRegistryImpl kitRegistry;
    private CooldownServiceImpl cooldownService;
    private EffectServiceImpl effectService;
    private ChatRouterImpl chatRouterImpl;
    private MatchBroadcasterImpl matchBroadcasterImpl;
    private MapPersistence mapPersistence;
    private MapEditingService mapEditingService;
    private CoordinationBackend coordinationBackend;
    private ArenaHeartbeater heartbeater;
    private Matchmaker matchmaker;
    private DashboardServer dashboardServer;
    private BungeeChannelTransport proxyTransport;
    private StatsRegistryImpl statsRegistry;
    private QueueBossbarService queueBossbarService;

    @Override
    public void onEnable() {
        try {
            saveDefaultResources();
            loadConfigAndTheme();

            this.audiences = BukkitAudiences.create(this);
            this.messageService = new MessageServiceImpl(
                    audiences,
                    loadAllBundles(),
                    config.i18n().defaultLocale(),
                    config.i18n().fallbackChain(),
                    theme);

            this.databaseService = new DatabaseServiceImpl(config.database(), getLogger());
            this.databaseService.registerMigrations(this, "core", CORE_MIGRATIONS);

            this.playerProfileService = new PlayerProfileServiceImpl(databaseService.dataSource(), getLogger());
            getServer().getPluginManager()
                    .registerEvents(new ProfileLifecycleListener(playerProfileService), this);

            MapServiceImpl mapServiceImpl = new MapServiceImpl(getLogger());
            this.mapService = mapServiceImpl;
            this.mapPersistence = new MapPersistence(
                    getDataFolder().toPath().resolve("maps"), getLogger(),
                    mapServiceImpl.extensionRegistry());
            this.mapEditingService = new MapEditingService(this, mapService, mapPersistence,
                    mapServiceImpl.extensionRegistry(), getLogger());
            this.mapEditingService.purgeOrphanedEditWorlds();
            // Deferred to next tick so game plugins have a chance to register their
            // MapExtensions in their own onEnable before we deserialize maps from YAML.
            org.bukkit.Bukkit.getScheduler().runTask(this,
                    () -> this.mapEditingService.loadAllAndRegister());
            this.gameRegistry = new GameRegistryImpl(
                    def -> {
                        if (poolManager != null)
                            poolManager.preload(def);
                        if (coordinationBackend != null)
                            publishHostedGames();
                    },
                    def -> {
                        if (coordinationBackend != null)
                            publishHostedGames();
                    });
            this.arenaManager = new ArenaManagerImpl(this, gameRegistry, mapService, getLogger());

            this.teamManager = new TeamManagerImpl(arenaManager, gameRegistry);
            this.kitRegistry = new KitRegistryImpl();
            this.cooldownService = new CooldownServiceImpl(this);
            this.cooldownService.start();
            this.effectService = new EffectServiceImpl();
            this.statsRegistry = new StatsRegistryImpl();

            this.chatRouterImpl = new ChatRouterImpl(arenaManager, teamManager);
            this.matchBroadcasterImpl = new MatchBroadcasterImpl(messageService);

            getServer().getPluginManager().registerEvents(cooldownService, this);
            getServer().getPluginManager().registerEvents(
                    new AbilityTriggerListener(kitRegistry, arenaManager, cooldownService), this);
            getServer().getPluginManager()
                    .registerEvents(new ArenaDisconnectListener(arenaManager), this);
            getServer().getPluginManager().registerEvents(chatRouterImpl, this);

            KitSelectorGui kitSelectorGui = new KitSelectorGui(this, kitRegistry, messageService);
            getServer().getPluginManager().registerEvents(
                    new KitSelectorListener(this, kitSelectorGui, kitRegistry, messageService), this);

            this.lifecycleTicker = new LifecycleTicker(this, arenaManager, gameRegistry, messageService);
            this.lifecycleTicker.start();

            // Coordination backend: in-memory for standalone, Redis otherwise.
            // Must come before the pool manager - pool manager reads from it in
            // central mode.
            this.coordinationBackend = createCoordinationBackend();
            this.poolManager = new ArenaPoolManager(this, arenaManager, gameRegistry, mapService,
                    coordinationBackend, config.arenaPool(), getLogger());
            this.poolManager.start();

            this.heartbeater = new ArenaHeartbeater(this, arenaManager, coordinationBackend);
            this.heartbeater.start();
            // Refresh hosted-games set every 20s so Redis entries don't TTL out (30s TTL).
            org.bukkit.Bukkit.getScheduler().runTaskTimer(this,
                    this::publishHostedGames, 20L * 20L, 20L * 20L);
            // Mirror local Bukkit events into the coordination channel so the
            // dashboard sees a network-wide feed; executor listens for control
            // commands addressed at this server. Both run on every backend.
            getServer().getPluginManager().registerEvents(
                    new me.playbosswar.tea.core.impl.coordination.LocalEventMirror(coordinationBackend), this);
            new me.playbosswar.tea.core.impl.coordination.ControlExecutor(
                    this, coordinationBackend, getLogger()).start();
            this.proxyTransport = new BungeeChannelTransport(this);
            this.matchmaker = new Matchmaker(this, coordinationBackend, gameRegistry, arenaManager, proxyTransport);
            this.matchmaker.start();

            this.queueBossbarService = new QueueBossbarService(this, coordinationBackend, gameRegistry, messageService);
            getServer().getPluginManager().registerEvents(queueBossbarService, this);
            this.queueBossbarService.start();

            PluginCommand teaCommand = getCommand("tea");
            if (teaCommand != null) {
                TeaCommand exec = new TeaCommand(this);
                teaCommand.setExecutor(exec);
                teaCommand.setTabCompleter(exec);
            }
            PluginCommand chatCommand = getCommand("chat");
            if (chatCommand != null) {
                TeaChatCommand exec = new TeaChatCommand(this);
                chatCommand.setExecutor(exec);
                chatCommand.setTabCompleter(exec);
            }
            PluginCommand kitCommand = getCommand("kit");
            if (kitCommand != null) {
                me.playbosswar.tea.core.impl.command.KitCommand exec =
                        new me.playbosswar.tea.core.impl.command.KitCommand(this, kitSelectorGui);
                kitCommand.setExecutor(exec);
                kitCommand.setTabCompleter(exec);
            }
            this.dashboardServer = new DashboardServer(this, this, config.dashboard(), getLogger());
            this.dashboardServer.start();

            TeaCoreAPI.set(this);
            getLogger().info("TeaCore enabled (v" + getDescription().getVersion() + ")");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "TeaCore failed to start", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        TeaCoreAPI.set(null);
        try {
            if (dashboardServer != null)
                dashboardServer.stop();
        } catch (Exception ignored) {
        }
        try {
            if (queueBossbarService != null)
                queueBossbarService.stop();
        } catch (Exception ignored) {
        }
        try {
            if (matchmaker != null)
                matchmaker.stop();
        } catch (Exception ignored) {
        }
        try {
            if (heartbeater != null)
                heartbeater.stop();
        } catch (Exception ignored) {
        }
        try {
            if (cooldownService != null)
                cooldownService.stop();
        } catch (Exception ignored) {
        }
        try {
            if (lifecycleTicker != null)
                lifecycleTicker.stop();
        } catch (Exception ignored) {
        }
        try {
            if (poolManager != null)
                poolManager.stop();
        } catch (Exception ignored) {
        }
        try {
            if (arenaManager != null) {
                for (var arena : List.copyOf(arenaManager.all())) {
                    arenaManager.release(arena.id());
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to release arenas cleanly", e);
        }
        try {
            if (coordinationBackend != null)
                coordinationBackend.close();
        } catch (Exception ignored) {
        }
        try {
            if (databaseService != null)
                databaseService.close();
        } catch (Exception ignored) {
        }
        try {
            if (messageService != null)
                messageService.close();
        } catch (Exception ignored) {
        }
        getLogger().info("TeaCore disabled");
    }

    private CoordinationBackend createCoordinationBackend() {
        String serverId = config.network().serverId();
        if (config.network().mode() == CoreConfig.NetworkMode.STANDALONE) {
            getLogger().info("Coordination: in-memory (server-id=" + serverId + ")");
            return new InMemoryBackend(serverId);
        }
        try {
            return new RedisBackend(serverId, config.redis(), getLogger());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "Redis coordination failed; falling back to in-memory backend. Cross-server features disabled.", e);
            return new InMemoryBackend(serverId);
        }
    }

    public void reload() {
        loadConfigAndTheme();
        messageService.replaceTheme(theme);
        messageService.replaceBundles(loadAllBundles());
    }

    /**
     * Re-reads all map YAMLs from disk and re-registers them, replacing in-memory
     * state.
     */
    public void reloadMaps() {
        if (mapService == null || mapEditingService == null)
            return;
        for (var def : List.copyOf(mapService.all())) {
            mapService.unregister(def.gameId(), def.mapId());
        }
        mapEditingService.loadAllAndRegister();
    }

    public MapEditingService mapEditingService() {
        return mapEditingService;
    }

    /**
     * Pushes the set of registered games for this backend into the coordination
     * backend. Called on every game register/unregister and from a 20s heartbeat
     * so Redis TTL entries don't expire.
     */
    private void publishHostedGames() {
        if (coordinationBackend == null || gameRegistry == null) return;
        java.util.Set<me.playbosswar.tea.core.api.game.GameId> ids = new java.util.HashSet<>();
        for (var def : gameRegistry.all()) ids.add(def.id());
        coordinationBackend.publishHostedGames(ids);
    }

    @Override
    public String version() {
        return getDescription().getVersion();
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    @Override
    public Theme theme() {
        return theme;
    }

    @Override
    public DatabaseService database() {
        return databaseService;
    }

    @Override
    public PlayerProfileService playerProfiles() {
        return playerProfileService;
    }

    @Override
    public GameRegistry games() {
        return gameRegistry;
    }

    @Override
    public MapService maps() {
        return mapService;
    }

    @Override
    public ArenaManager arenas() {
        return arenaManager;
    }

    @Override
    public TeamManager teams() {
        return teamManager;
    }

    @Override
    public KitRegistry kits() {
        return kitRegistry;
    }

    @Override
    public CooldownService cooldowns() {
        return cooldownService;
    }

    @Override
    public EffectService effects() {
        return effectService;
    }

    @Override
    public ChatRouter chatRouter() {
        return chatRouterImpl;
    }

    @Override
    public MatchBroadcaster matchBroadcaster() {
        return matchBroadcasterImpl;
    }

    @Override
    public CoordinationBackend coordination() {
        return coordinationBackend;
    }

    @Override
    public ProxyTransport proxyTransport() {
        return proxyTransport;
    }

    @Override
    public StatsRegistry stats() {
        return statsRegistry;
    }

    @Override
    public String serverId() {
        return config.network().serverId();
    }

    @Override
    public boolean isStandalone() {
        return config.network().mode() == CoreConfig.NetworkMode.STANDALONE;
    }

    @Override
    public String lobbyServerName() {
        return config.network().lobbyServer();
    }

    private void saveDefaultResources() {
        saveDefaultConfig();
        saveResource("theme.yml", false);
        for (String locale : BUNDLED_LOCALES) {
            saveResource("lang/" + locale + ".yml", false);
        }
    }

    private void loadConfigAndTheme() {
        Path dataFolder = getDataFolder().toPath();
        this.config = ConfigLoader.load(dataFolder.resolve("config.yml"), dataFolder, getLogger());
        this.theme = (ThemeImpl) ThemeLoader.load(dataFolder.resolve("theme.yml"));
    }

    private Map<String, LocaleBundle> loadAllBundles() {
        Map<String, LocaleBundle> bundles = new HashMap<>();
        Path dataFolder = getDataFolder().toPath();
        for (String locale : BUNDLED_LOCALES) {
            bundles.put(locale, LocaleBundleLoader.load(this, dataFolder, locale));
        }
        return bundles;
    }
}
