package me.playbosswar.tea.core.impl;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import dev.faststats.core.data.Metric;
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
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import me.playbosswar.tea.core.api.effect.EffectService;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.achievement.AchievementService;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.persistence.DatabaseService;
import me.playbosswar.tea.core.api.placeholder.PlaceholderService;
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
import me.playbosswar.tea.core.impl.economy.VaultEconomyService;
import me.playbosswar.tea.core.impl.effect.EffectServiceImpl;
import me.playbosswar.tea.core.impl.game.GameRegistryImpl;
import me.playbosswar.tea.core.impl.kit.AbilityTriggerListener;
import me.playbosswar.tea.core.impl.kit.KitRegistryImpl;
import me.playbosswar.tea.core.impl.kit.KitSelectorGui;
import me.playbosswar.tea.core.impl.kit.KitSelectorListener;
import me.playbosswar.tea.core.impl.map.MapEditingService;
import me.playbosswar.tea.core.impl.map.MapPersistence;
import me.playbosswar.tea.core.impl.map.MapServiceImpl;
import me.playbosswar.tea.core.impl.map.MapSetupSidebar;
import me.playbosswar.tea.core.impl.placeholder.CorePlaceholders;
import me.playbosswar.tea.core.impl.placeholder.GameStatsPlaceholders;
import me.playbosswar.tea.core.impl.placeholder.PlaceholderServiceImpl;
import me.playbosswar.tea.core.impl.placeholder.TeaPlaceholderExpansion;
import me.playbosswar.tea.core.impl.scoreboard.ScoreboardManager;
import me.playbosswar.tea.core.impl.achievement.AchievementServiceImpl;
import me.playbosswar.tea.core.impl.stats.CoreStatsService;
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
    private static final List<String> CORE_MIGRATIONS = List.of(
            "V001__init.sql",
            "V002__kit_unlocks.sql",
            "V003__lobby_signs.sql",
            "V004__stats_schemas.sql",
            "V005__core_stats.sql",
            "V006__achievements.sql",
            "V007__core_assists.sql",
            "V008__match_history.sql");

    /**
     * FastStats error tracker. Context-aware so it only captures throwables from
     * TeaCore's classloader, never from other plugins running in the same JVM.
     */
    public static final ErrorTracker ERROR_TRACKER = ErrorTracker.contextAware();

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
    private VaultEconomyService economyService;
    private me.playbosswar.tea.core.impl.lobby.command.TeaLobbyCommand lobbyAdminCommand;
    // True once the optional lobby features (signs/spawn/play) have been wired up.
    // Guards against double-registration when /tea reload re-enables them at runtime.
    private boolean lobbyFeaturesActive;
    private EffectServiceImpl effectService;
    private ChatRouterImpl chatRouterImpl;
    private MatchBroadcasterImpl matchBroadcasterImpl;
    private MapPersistence mapPersistence;
    private MapEditingService mapEditingService;
    private ScoreboardManager scoreboardManager;
    private MapSetupSidebar mapSetupSidebar;
    private CoordinationBackend coordinationBackend;
    private ArenaHeartbeater heartbeater;
    private Matchmaker matchmaker;
    private DashboardServer dashboardServer;
    private BungeeChannelTransport proxyTransport;
    private StatsRegistryImpl statsRegistry;
    private CoreStatsService coreStatsService;
    private AchievementServiceImpl achievementService;
    private me.playbosswar.tea.core.impl.combat.CombatTrackerImpl combatTracker;
    private me.playbosswar.tea.core.impl.loot.LootServiceImpl lootService;
    private me.playbosswar.tea.core.impl.feature.FeatureServiceImpl featureService;
    private me.playbosswar.tea.core.impl.sidebar.ArenaValues arenaValues;
    private me.playbosswar.tea.core.impl.sidebar.SidebarServiceImpl sidebarService;
    private org.bukkit.configuration.file.YamlConfiguration sidebarYaml;
    private me.playbosswar.tea.core.impl.placeholder.LeaderboardCache leaderboardCache;
    private me.playbosswar.tea.core.impl.menu.MenuServiceImpl menuService;
    private me.playbosswar.tea.core.impl.menu.CoreMenus coreMenus;
    private me.playbosswar.tea.core.impl.history.MatchHistoryServiceImpl matchHistory;
    private PlaceholderServiceImpl placeholderService;
    private QueueBossbarService queueBossbarService;

    // Declared after the fields it reads so suppliers don't trip Java's
    // forward-reference rule. Suppliers themselves run lazily on each FastStats
    // flush (~30 min), well after onEnable, so reading getConfig() and instance
    // fields is safe even though this is a field initializer.
    private final BukkitMetrics metrics = BukkitMetrics.factory()
            .token("3094ede1b35f5b42406db8387caaa65c")
            .errorTracker(ERROR_TRACKER)
            .addMetric(Metric.string("database_type",
                    () -> getConfig().getString("database.type", "sqlite")))
            .addMetric(Metric.bool("dashboard_enabled",
                    () -> getConfig().getBoolean("dashboard.enabled", true)))
            .addMetric(Metric.string("network_mode",
                    () -> getConfig().getString("network.mode", "standalone")))
            .addMetric(Metric.string("arena_pool_mode",
                    () -> getConfig().getString("arena-pool.mode", "standalone")))
            .addMetric(Metric.string("default_locale",
                    () -> getConfig().getString("i18n.default-locale", "en_US")))
            .addMetric(Metric.string("coordination_backend", () -> {
                if (coordinationBackend == null) return "uninitialized";
                return coordinationBackend instanceof RedisBackend ? "redis" : "in_memory";
            }))
            .addMetric(Metric.number("registered_games_count",
                    () -> gameRegistry == null ? 0 : gameRegistry.all().size()))
            .create(this);

    @Override
    public void onEnable() {
        metrics.ready();
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
            this.scoreboardManager = new ScoreboardManager(this);
            getServer().getPluginManager().registerEvents(scoreboardManager, this);
            if (getConfig().getBoolean("map-setup-scoreboard.enabled", true)) {
                this.mapSetupSidebar = new MapSetupSidebar(mapEditingService, messageService);
                this.scoreboardManager.register(mapSetupSidebar);
            }
            this.scoreboardManager.start();
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
            arenaManager.setTeamManager(teamManager);
            this.kitRegistry = new KitRegistryImpl(databaseService.dataSource(), getLogger());
            this.cooldownService = new CooldownServiceImpl(this);
            this.cooldownService.start();

            // Vault is a hard dep, but the Economy service provider is usually
            // registered by a separate plugin that may load after TeaCore. Resolve
            // on the next tick so all plugins have had a chance to enable.
            this.economyService = new VaultEconomyService(getLogger());
            org.bukkit.Bukkit.getScheduler().runTask(this, () -> {
                if (!this.economyService.isAvailable()) {
                    this.economyService = new VaultEconomyService(getLogger());
                }
            });
            this.effectService = new EffectServiceImpl();
            this.statsRegistry = new StatsRegistryImpl(databaseService.dataSource(), getLogger());
            this.combatTracker = new me.playbosswar.tea.core.impl.combat.CombatTrackerImpl(
                    this, arenaManager, teamManager);
            getServer().getPluginManager().registerEvents(combatTracker, this);
            this.lootService = new me.playbosswar.tea.core.impl.loot.LootServiceImpl(getLogger());
            this.featureService = new me.playbosswar.tea.core.impl.feature.FeatureServiceImpl(this);
            this.arenaValues = new me.playbosswar.tea.core.impl.sidebar.ArenaValues(
                    this, () -> getConfig().getString("i18n.default-locale", "en_US"));
            getServer().getPluginManager().registerEvents(arenaValues, this);
            loadSidebarYaml();
            this.sidebarService = new me.playbosswar.tea.core.impl.sidebar.SidebarServiceImpl(
                    this, scoreboardManager, featureService, arenaValues,
                    () -> sidebarYaml.getConfigurationSection("lobby"),
                    () -> getConfig().getBoolean("lobby.enabled", false));
            this.menuService = new me.playbosswar.tea.core.impl.menu.MenuServiceImpl(
                    this, messageService, featureService, getLogger());
            getServer().getPluginManager().registerEvents(menuService, this);
            me.playbosswar.tea.core.impl.menu.GameDisplay gameDisplay =
                    new me.playbosswar.tea.core.impl.menu.GameDisplay(this, this);
            arenaValues.setGameDisplay(gameDisplay);
            this.coreMenus = new me.playbosswar.tea.core.impl.menu.CoreMenus(this, gameDisplay);
            coreMenus.register(menuService);
            this.matchHistory = new me.playbosswar.tea.core.impl.history.MatchHistoryServiceImpl(
                    this, this, databaseService.dataSource(), getLogger());
            getServer().getPluginManager().registerEvents(matchHistory, this);
            new me.playbosswar.tea.core.impl.menu.PlayMenus(this, this, matchHistory, gameDisplay).register(menuService);
            for (String file : List.of("kits", "stats", "play", "play-maps", "arenas", "profile",
                    "profile-kits", "achievements", "history")) {
                menuService.registerMenu(this, "menus/" + file + ".yml");
            }
            getServer().getPluginManager().registerEvents(
                    new me.playbosswar.tea.core.impl.lobby.hotbar.LobbyHotbarService(this, this), this);
            PluginCommand profileCommand = getCommand("profile");
            if (profileCommand != null) {
                profileCommand.setExecutor((sender, cmd, label, args) -> {
                    if (sender instanceof org.bukkit.entity.Player player) {
                        if (!menuService.open(player, "profile", java.util.Map.of())) {
                            player.performCommand("stats");
                        }
                    } else {
                        messageService.sendToSender(sender, "core.command.player-only");
                    }
                    return true;
                });
            }
            menuService.loadCustomMenus();
            menuService.start();
            PluginCommand menuCommand = getCommand("menu");
            if (menuCommand != null) {
                var exec = new me.playbosswar.tea.core.impl.command.MenuCommand(this, menuService);
                menuCommand.setExecutor(exec);
                menuCommand.setTabCompleter(exec);
            }
            PluginCommand boardCommand = getCommand("board");
            if (boardCommand != null) {
                boardCommand.setExecutor((sender, cmd, label, args) -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        messageService.sendToSender(sender, "core.command.player-only");
                        return true;
                    }
                    boolean hide = !sidebarService.isHidden(player);
                    sidebarService.setHidden(player, hide);
                    messageService.send(player, hide ? "core.board.hidden" : "core.board.shown");
                    return true;
                });
            }
            this.coreStatsService = new CoreStatsService(
                    this, databaseService.dataSource(), arenaManager, teamManager, getLogger());
            getServer().getPluginManager().registerEvents(coreStatsService, this);
            this.achievementService = new AchievementServiceImpl(
                    databaseService.dataSource(), getLogger(), messageService);

            // PlaceholderAPI integration. Built-in resolvers cover core/player
            // stats and every game's stats; games add their own via
            // placeholders().register(...). The PAPI expansion is only wired up
            // when PlaceholderAPI is installed - resolution still works for
            // internal callers either way.
            this.placeholderService = new PlaceholderServiceImpl();
            this.leaderboardCache = new me.playbosswar.tea.core.impl.placeholder.LeaderboardCache(
                    this, databaseService.dataSource(), statsRegistry, getLogger());
            leaderboardCache.start();
            placeholderService.registerGlobal(new CorePlaceholders(this, coreStatsService, arenaValues, leaderboardCache));
            placeholderService.registerGlobal(new GameStatsPlaceholders(statsRegistry));
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null
                    && getConfig().getBoolean("features.placeholders", true)) {
                try {
                    new TeaPlaceholderExpansion(placeholderService, version()).register();
                    getLogger().info("Registered PlaceholderAPI expansion 'tea'.");
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", t);
                    ERROR_TRACKER.trackError(t);
                }
            }

            this.chatRouterImpl = new ChatRouterImpl(arenaManager, teamManager);
            this.matchBroadcasterImpl = new MatchBroadcasterImpl(messageService);

            getServer().getPluginManager().registerEvents(cooldownService, this);
            getServer().getPluginManager().registerEvents(
                    new AbilityTriggerListener(kitRegistry, arenaManager, cooldownService), this);
            getServer().getPluginManager().registerEvents(chatRouterImpl, this);

            KitSelectorGui kitSelectorGui = new KitSelectorGui(this, kitRegistry, messageService, economyService);
            getServer().getPluginManager().registerEvents(
                    new KitSelectorListener(this, kitSelectorGui, kitRegistry, messageService, economyService), this);

            this.lifecycleTicker = new LifecycleTicker(this, arenaManager, gameRegistry, messageService);
            this.lifecycleTicker.start();

            // Coordination backend: in-memory for standalone, Redis otherwise.
            // Must come before the pool manager - pool manager reads from it in
            // central mode.
            this.coordinationBackend = createCoordinationBackend();
            this.proxyTransport = new BungeeChannelTransport(this);
            // Build the matchmaker before the disconnect listener so the
            // listener can ask "is this player mid-routing?" and skip the
            // queue dequeue during a proxy hop.
            this.matchmaker = new Matchmaker(this, coordinationBackend, gameRegistry, arenaManager, proxyTransport);
            // Disconnect handling: clean up the player's arena membership AND
            // drop their queue entry so a reconnect doesn't auto-route into a
            // stale match. The matchmaker reference lets us recognize a
            // legitimate cross-server hop and skip the dequeue in that case.
            getServer().getPluginManager().registerEvents(
                    new ArenaDisconnectListener(arenaManager, coordinationBackend, matchmaker), this);
            this.poolManager = new ArenaPoolManager(this, arenaManager, gameRegistry, mapService,
                    coordinationBackend, config.arenaPool(),
                    config.network().eligibleMatchmaker(), getLogger());
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
            // Matchmaker also listens for PlayerJoinEvent so it can route a
            // queued player into an arena immediately on arrival from a proxy
            // hop, instead of waiting up to 1s for the next scheduled tick.
            getServer().getPluginManager().registerEvents(this.matchmaker, this);
            this.matchmaker.start();

            this.queueBossbarService = new QueueBossbarService(this, coordinationBackend, messageService);
            getServer().getPluginManager().registerEvents(queueBossbarService, this);
            this.queueBossbarService.start();

            PluginCommand teaCommand = getCommand("tea");
            if (teaCommand != null) {
                TeaCommand exec = new TeaCommand(this);
                teaCommand.setExecutor(exec);
                teaCommand.setTabCompleter(exec);
            }
            // /chat exists to switch between match/team/staff/global channels and
            // is meaningless on a lobby (no in-progress match, no team). When
            // lobby.enabled is true enableLobbyFeatures() hides it from the command
            // map; otherwise wire up its executor here.
            boolean lobbyEnabled = getConfig().getBoolean("lobby.enabled", false);
            if (!lobbyEnabled) {
                PluginCommand chatCommand = getCommand("chat");
                if (chatCommand != null) {
                    TeaChatCommand exec = new TeaChatCommand(this);
                    chatCommand.setExecutor(exec);
                    chatCommand.setTabCompleter(exec);
                }
            }
            PluginCommand kitCommand = getCommand("kit");
            if (kitCommand != null) {
                me.playbosswar.tea.core.impl.command.KitCommand exec = new me.playbosswar.tea.core.impl.command.KitCommand(
                        this, kitSelectorGui);
                kitCommand.setExecutor(exec);
                kitCommand.setTabCompleter(exec);
            }
            PluginCommand leaveCommand = getCommand("leave");
            if (leaveCommand != null) {
                me.playbosswar.tea.core.impl.command.LeaveCommand exec = new me.playbosswar.tea.core.impl.command.LeaveCommand(
                        this);
                leaveCommand.setExecutor(exec);
                leaveCommand.setTabCompleter(exec);
            }
            me.playbosswar.tea.core.impl.stats.PlayerStatsGui statsGui = new me.playbosswar.tea.core.impl.stats.PlayerStatsGui(
                    this, statsRegistry, messageService);
            getServer().getPluginManager()
                    .registerEvents(new me.playbosswar.tea.core.impl.stats.PlayerStatsListener(), this);
            PluginCommand statsCommand = getCommand("stats");
            if (statsCommand != null) {
                var exec = new me.playbosswar.tea.core.impl.command.PlayerStatsCommand(this, statsGui);
                statsCommand.setExecutor(exec);
                statsCommand.setTabCompleter(exec);
            }
            this.dashboardServer = new DashboardServer(this, this, config.dashboard(), getLogger());
            this.dashboardServer.start();

            // Optional lobby features (formerly TeaLobby plugin). Gated by
            // lobby.enabled in config.yml — disabled by default so a fresh
            // TeaCore install on an arena server isn't surprised. Enable on
            // each lobby/hub server that needs /play, connection signs,
            // spawn auto-management.
            if (lobbyEnabled) {
                enableLobbyFeatures();
            }

            TeaCoreAPI.set(this);
            getLogger().info("TeaCore enabled (v" + getDescription().getVersion() + ")");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "TeaCore failed to start", e);
            ERROR_TRACKER.trackError(e);
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
            if (scoreboardManager != null)
                scoreboardManager.stop();
            if (leaderboardCache != null)
                leaderboardCache.stop();
            if (menuService != null)
                menuService.stop();
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
            ERROR_TRACKER.trackError(e);
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
        try {
            metrics.shutdown();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error shutting down FastStats metrics", e);
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
            ERROR_TRACKER.trackError(e);
            return new InMemoryBackend(serverId);
        }
    }

    /**
     * Wires up the optional lobby features (formerly the TeaLobby plugin):
     * connection signs, spawn auto-management, and the {@code /play} command.
     * Idempotent via {@link #lobbyFeaturesActive} so it can be called from
     * {@code onEnable} at startup and from {@link #reload()} when an admin flips
     * {@code lobby.enabled} on at runtime, without double-registering listeners.
     */
    private void enableLobbyFeatures() {
        if (lobbyFeaturesActive) return;
        // /chat switches match/team/staff/global channels and is meaningless on a
        // lobby, so hide it from tab-complete and /help when lobby mode is on.
        me.playbosswar.tea.core.impl.command.CommandRegistrar.unregister("chat", getLogger());

        me.playbosswar.tea.core.impl.lobby.sign.ConnectionSignService signService = new me.playbosswar.tea.core.impl.lobby.sign.ConnectionSignService(
                databaseService.dataSource(), getLogger());
        signService.loadAll();
        me.playbosswar.tea.core.impl.lobby.sign.ConnectionSignUpdater signUpdater = new me.playbosswar.tea.core.impl.lobby.sign.ConnectionSignUpdater(
                this, this, signService);
        signUpdater.start();
        getServer().getPluginManager().registerEvents(
                new me.playbosswar.tea.core.impl.lobby.sign.ConnectionSignListener(this, signService), this);

        me.playbosswar.tea.core.impl.lobby.spawn.LobbySpawnService spawnService = new me.playbosswar.tea.core.impl.lobby.spawn.LobbySpawnService(
                this, this);
        getServer().getPluginManager().registerEvents(spawnService, this);

        PluginCommand playCommand = getCommand("play");
        if (playCommand != null) {
            var exec = new me.playbosswar.tea.core.impl.lobby.command.PlayCommand(this, this);
            playCommand.setExecutor(exec);
            playCommand.setTabCompleter(exec);
        }
        // Lobby admin handler is dispatched via /tea lobby ... (see TeaCommand);
        // there is no longer a top-level /tealobby command.
        this.lobbyAdminCommand = new me.playbosswar.tea.core.impl.lobby.command.TeaLobbyCommand(
                this, this, signService, spawnService);
        this.lobbyFeaturesActive = true;
        getLogger().info("Lobby features enabled.");
    }

    public void reload() {
        loadConfigAndTheme();
        reloadConfig();
        loadSidebarYaml();
        if (menuService != null) {
            menuService.reload();
        }
        messageService.replaceTheme(theme);
        messageService.replaceBundles(loadAllBundles());

        // Pick up a runtime flip of lobby.enabled. Enabling at reload is safe
        // (the wiring is idempotent); disabling is not — listeners and command
        // executors can't be cleanly torn down without a restart, so warn instead
        // of pretending it worked.
        boolean lobbyEnabled = getConfig().getBoolean("lobby.enabled", false);
        if (lobbyEnabled && !lobbyFeaturesActive) {
            enableLobbyFeatures();
        } else if (!lobbyEnabled && lobbyFeaturesActive) {
            getLogger().warning(
                    "lobby.enabled was turned off, but lobby features can't be torn down at runtime. "
                            + "Restart the server to fully disable them.");
        }
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

    public ScoreboardManager scoreboards() {
        return scoreboardManager;
    }

    /**
     * Map-setup sidebar provider, or {@code null} when disabled via
     * {@code map-setup-scoreboard.enabled}.
     */
    public MapSetupSidebar mapSetupSidebar() {
        return mapSetupSidebar;
    }

    /**
     * Lobby admin handler (`/tea lobby ...`); null when {@code lobby.enabled}
     * is false.
     */
    public me.playbosswar.tea.core.impl.lobby.command.TeaLobbyCommand lobbyAdminCommand() {
        return lobbyAdminCommand;
    }

    /**
     * Pushes the set of registered games for this backend into the coordination
     * backend. Called on every game register/unregister and from a 20s heartbeat
     * so Redis TTL entries don't expire.
     */
    private void publishHostedGames() {
        if (coordinationBackend == null || gameRegistry == null)
            return;
        java.util.Set<me.playbosswar.tea.core.api.game.GameId> ids = new java.util.HashSet<>();
        for (var def : gameRegistry.all())
            ids.add(def.id());
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
    public EconomyService economy() {
        return economyService;
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
    public PlaceholderService placeholders() {
        return placeholderService;
    }

    @Override
    public AchievementService achievements() {
        return achievementService;
    }

    @Override
    public me.playbosswar.tea.core.api.combat.CombatTracker combat() {
        return combatTracker;
    }

    @Override
    public me.playbosswar.tea.core.api.loot.LootService loot() {
        return lootService;
    }

    @Override
    public me.playbosswar.tea.core.api.feature.FeatureService features() {
        return featureService;
    }

    @Override
    public me.playbosswar.tea.core.api.sidebar.SidebarService sidebars() {
        return sidebarService;
    }

    /** Kit purchase/selection and other core menu logic (internal). */
    public me.playbosswar.tea.core.impl.menu.CoreMenus coreMenus() {
        return coreMenus;
    }

    /** TeaCore's game-agnostic stats (internal; used by the stats menu). */
    public CoreStatsService coreStats() {
        return coreStatsService;
    }

    @Override
    public me.playbosswar.tea.core.api.history.MatchHistoryService matchHistory() {
        return matchHistory;
    }

    @Override
    public me.playbosswar.tea.core.api.menu.MenuService menus() {
        return menuService;
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
        if (!getDataFolder().toPath().resolve("sidebar.yml").toFile().exists()) {
            saveResource("sidebar.yml", false);
        }
        for (String locale : BUNDLED_LOCALES) {
            saveResource("lang/" + locale + ".yml", false);
        }
        // Surface any new keys the latest jar shipped without overwriting
        // anything the admin already customized in their on-disk YAMLs.
        me.playbosswar.tea.core.api.util.ConfigSync.mergeMissingKeys(this);
        me.playbosswar.tea.core.api.util.ConfigSync.mergeMissingKeys(this, "theme.yml",
                getDataFolder().toPath().resolve("theme.yml"));
        me.playbosswar.tea.core.api.util.ConfigSync.mergeMissingKeys(this, "sidebar.yml",
                getDataFolder().toPath().resolve("sidebar.yml"));
    }

    /** (Re)reads plugins/TeaCore/sidebar.yml (the lobby sidebar layout). */
    private void loadSidebarYaml() {
        this.sidebarYaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                getDataFolder().toPath().resolve("sidebar.yml").toFile());
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
