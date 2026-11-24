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
            "V008__match_history.sql",
            "V009__dashboard.sql",
            "V010__achievement_definitions.sql",
            "V011__ratings.sql",
            "V012__quests.sql",
            "V013__shared_files.sql");

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
    private me.playbosswar.tea.core.impl.metrics.ServerStatsPublisher serverStats;
    private me.playbosswar.tea.core.impl.spectator.SpectatorTools spectatorTools;
    private me.playbosswar.tea.core.impl.arena.MapVoting mapVoting;
    private me.playbosswar.tea.core.impl.rating.RatingService ratings;
    private me.playbosswar.tea.core.impl.party.PartyService parties;

    public MapPersistence mapPersistence() {
        return mapPersistence;
    }

    public me.playbosswar.tea.core.impl.party.PartyService parties() {
        return parties;
    }

    public me.playbosswar.tea.core.impl.rating.RatingService ratings() {
        return ratings;
    }

    public me.playbosswar.tea.core.impl.arena.MapVoting mapVoting() {
        return mapVoting;
    }
    private LifecycleTicker lifecycleTicker;
    private ArenaPoolManager poolManager;
    private TeamManagerImpl teamManager;
    private KitRegistryImpl kitRegistry;
    private CooldownServiceImpl cooldownService;
    private VaultEconomyService economyService;
    private me.playbosswar.tea.core.impl.lobby.command.TeaLobbyCommand lobbyAdminCommand;
    // Guards against double-registration when /tea reload enables lobby features at runtime.
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
    private ProxyTransport proxyTransport;
    private me.playbosswar.tea.core.impl.cloud.CloudIntegration cloudIntegration;
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
    private me.playbosswar.tea.core.impl.map.editor.MapEditorTools mapEditorTools;
    private me.playbosswar.tea.core.impl.admin.ArenaAdmin arenaAdmin;
    private me.playbosswar.tea.core.impl.discord.DiscordWebhooks discordWebhooks;
    private me.playbosswar.tea.core.impl.discord.DiscordEvents discordEvents;
    private me.playbosswar.tea.core.impl.dashboard.auth.LoginCodes dashboardLoginCodes;
    private me.playbosswar.tea.core.impl.history.MatchHistoryServiceImpl matchHistory;
    private PlaceholderServiceImpl placeholderService;
    private QueueBossbarService queueBossbarService;

    // Declared after the fields it reads to satisfy Java's forward-reference rule.
    // Suppliers run lazily on each FastStats flush, long after onEnable, so reading config here is safe.
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
            platform(); // pick the version platform now, so a mismatch fails at startup

            this.audiences = BukkitAudiences.create(this);
            this.messageService = new MessageServiceImpl(
                    audiences,
                    loadAllBundles(),
                    config.i18n().defaultLocale(),
                    config.i18n().fallbackChain(),
                    theme);

            this.databaseService = new DatabaseServiceImpl(config.database(), getLogger());
            this.databaseService.registerMigrations(this, "core", CORE_MIGRATIONS);
            if (!"files".equalsIgnoreCase(getConfig().getString("maps.storage", "database"))) {
                this.sharedFiles = new me.playbosswar.tea.core.impl.map.SharedFiles(
                        databaseService.dataSource(), getDataFolder().toPath(), serverId(), getLogger());
                sharedFiles.pullAtStartup();
                me.playbosswar.tea.core.impl.map.MapChanges.setListener(this::pushSharedFiles);
            }
            this.settingsStore = new me.playbosswar.tea.core.impl.settings.SettingsStore(
                    databaseService.dataSource(), getLogger());
            // Before any game plugin is enabled, so they read the shared values.
            settingsStore.applyToFiles();
            reloadConfig();

            this.playerProfileService = new PlayerProfileServiceImpl(databaseService.dataSource(), getLogger());
            this.dashboardLoginCodes = new me.playbosswar.tea.core.impl.dashboard.auth.LoginCodes(
                    databaseService.dataSource(), getLogger());
            getServer().getPluginManager()
                    .registerEvents(new ProfileLifecycleListener(playerProfileService, this), this);

            MapServiceImpl mapServiceImpl = new MapServiceImpl(getLogger());
            this.mapService = mapServiceImpl;
            this.mapPersistence = new MapPersistence(
                    getDataFolder().toPath().resolve("maps"), getLogger(),
                    mapServiceImpl.extensionRegistry());
            this.mapEditingService = new MapEditingService(this, mapService, mapPersistence,
                    mapServiceImpl.extensionRegistry(), getLogger());
            this.mapEditingService.purgeOrphanedEditWorlds();
            this.mapEditingService.setBackups(new me.playbosswar.tea.core.impl.map.MapBackups(
                    mapPersistence, () -> getConfig().getInt("maps.keep-versions", 10), getLogger()));
            // Next tick, so game plugins can register their MapExtensions before maps are deserialized.
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
            this.hosting = new me.playbosswar.tea.core.impl.arena.Hosting(this, gameRegistry);
            arenaManager.setHosting(hosting);

            this.teamManager = new TeamManagerImpl(arenaManager, gameRegistry);
            this.parties = new me.playbosswar.tea.core.impl.party.PartyService(this, getLogger());
            // Party plugins may enable after TeaCore.
            getServer().getScheduler().runTask(this, parties::detect);
            teamManager.setPartyMates(uuid -> parties.partyOf(uuid)
                    .map(p -> (java.util.Set<java.util.UUID>) p.members()).orElse(java.util.Set.of()));
            arenaManager.setTeamManager(teamManager);
            this.kitRegistry = new KitRegistryImpl(databaseService.dataSource(), getLogger());
            this.cooldownService = new CooldownServiceImpl(this);
            this.cooldownService.start();

            // The Vault Economy provider is registered by another plugin that may load
            // after TeaCore, so it is resolved on the next tick.
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
            setUpBedrockForms();
            me.playbosswar.tea.core.impl.menu.GameDisplay gameDisplay =
                    new me.playbosswar.tea.core.impl.menu.GameDisplay(this, this);
            arenaValues.setGameDisplay(gameDisplay);
            this.coreMenus = new me.playbosswar.tea.core.impl.menu.CoreMenus(this, gameDisplay);
            coreMenus.register(menuService);
            this.matchHistory = new me.playbosswar.tea.core.impl.history.MatchHistoryServiceImpl(
                    this, this, databaseService.dataSource(), getLogger());
            getServer().getPluginManager().registerEvents(matchHistory, this);
            matchHistory.startRetention(getConfig().getInt("match-history.keep-days", 90));
            new me.playbosswar.tea.core.impl.menu.PlayMenus(this, this, matchHistory, gameDisplay).register(menuService);
            this.mapEditorTools = new me.playbosswar.tea.core.impl.map.editor.MapEditorTools(
                    this, mapEditingService, messageService, featureService);
            mapEditorTools.start(menuService);
            for (String file : List.of("kits", "stats", "play", "play-maps", "arenas", "profile",
                    "profile-kits", "achievements", "history", "confirm", "map-editor")) {
                menuService.registerMenu(this, "menus/" + file + ".yml");
            }
            this.spectatorTools = new me.playbosswar.tea.core.impl.spectator.SpectatorTools(this, this, featureService);
            getServer().getPluginManager().registerEvents(spectatorTools, this);
            menuService.registerMenu(this, "menus/spectate.yml");
            menuService.registerMenu(this, "menus/map-vote.yml");
            spectatorTools.start(menuService);
            arenaValues.setWatching(spectatorTools::watching);
            sidebarService.setSpectatorSidebar(() -> sidebarYaml.getConfigurationSection("spectator"),
                    p -> spectatorTools.isGhost(p.getUniqueId()));
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
            PluginCommand voteCommand = getCommand("vote");
            if (voteCommand != null) {
                voteCommand.setExecutor((sender, cmd, label, args) -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        messageService.sendToSender(sender, "core.command.player-only");
                        return true;
                    }
                    if (mapVoting == null || mapVoting.votingArena(player).isEmpty()) {
                        messageService.send(player, "core.vote.none");
                    } else if (args.length > 0) {
                        mapVoting.vote(player, args[0]);
                    } else if (!menuService.open(player, "map-vote", java.util.Map.of())) {
                        messageService.send(player, "core.vote.prompt",
                                java.util.Map.of("maps", mapVoting.summary(player)));
                    }
                    return true;
                });
                voteCommand.setTabCompleter((sender, cmd, label, args) -> sender instanceof org.bukkit.entity.Player p
                        && mapVoting != null && args.length == 1
                        ? mapVoting.options(p).stream().filter(o -> o.startsWith(args[0])).toList() : List.of());
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
            this.discordWebhooks = new me.playbosswar.tea.core.impl.discord.DiscordWebhooks(this, getLogger());
            this.discordEvents = new me.playbosswar.tea.core.impl.discord.DiscordEvents(this, discordWebhooks);
            getServer().getPluginManager().registerEvents(discordEvents, this);
            achievementService.setUnlockListener(discordEvents::achievement);

            // The PAPI expansion is only wired up when PlaceholderAPI is installed;
            // internal resolution works either way.
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
            this.mapVoting = new me.playbosswar.tea.core.impl.arena.MapVoting(this, this, arenaManager, featureService);
            getServer().getPluginManager().registerEvents(mapVoting, this);
            lifecycleTicker.setHoldBeforeStart(mapVoting::holdBeforeStart);
            mapVoting.register(menuService);
            getServer().getPluginManager().registerEvents(
                    new me.playbosswar.tea.core.impl.arena.WaitingLobby(this, this, featureService), this);
            getServer().getPluginManager().registerEvents(
                    new me.playbosswar.tea.core.impl.lobby.pvp.LobbyPvpListener(this, this), this);
            this.npcService = new me.playbosswar.tea.core.impl.npc.NpcServiceImpl(this, this);
            npcService.loadConfigNpcs();
            getServer().getPluginManager().registerEvents(npcService, this);

            // Must be created before the pool manager, which reads from it in central mode.
            this.coordinationBackend = createCoordinationBackend();
            this.serverStats = new me.playbosswar.tea.core.impl.metrics.ServerStatsPublisher(
                    this, coordinationBackend, arenaManager::spareCount);
            serverStats.start();
            me.playbosswar.tea.core.impl.cloud.CloudPlatform cloud = getConfig().getBoolean("cloudnet.enabled", true)
                    ? me.playbosswar.tea.core.impl.cloud.CloudPlatform.detect(getLogger()) : null;
            if (cloud != null) {
                cloudIntegration = new me.playbosswar.tea.core.impl.cloud.CloudIntegration(this, cloud, arenaManager,
                        coordinationBackend);
                this.proxyTransport = cloudIntegration.transport(config.network().lobbyServer());
                cloudIntegration.start();
            } else {
                this.proxyTransport = new BungeeChannelTransport(this);
            }
            // Built before the disconnect listener so it can skip the queue dequeue during a proxy hop.
            this.matchmaker = new Matchmaker(this, coordinationBackend, gameRegistry, arenaManager, proxyTransport);
            // Drops the queue entry on disconnect so a reconnect doesn't route into a stale match.
            getServer().getPluginManager().registerEvents(
                    new ArenaDisconnectListener(arenaManager, coordinationBackend, matchmaker), this);
            this.poolManager = new ArenaPoolManager(this, arenaManager, gameRegistry, mapService,
                    coordinationBackend, config.arenaPool(),
                    config.network().eligibleMatchmaker(), getLogger());
            this.poolManager.setHosting(hosting);
            this.poolManager.start();

            this.heartbeater = new ArenaHeartbeater(this, arenaManager, coordinationBackend);
            this.heartbeater.start();
            this.ratings = new me.playbosswar.tea.core.impl.rating.RatingService(
                    this, this, databaseService.dataSource(), getLogger());
            getServer().getPluginManager().registerEvents(ratings, this);
            heartbeater.setRatings(ratings);
            matchmaker.setRatings(ratings);
            registerLeaderboard();
            registerQuests();
            // Every 20s so the Redis entries (30s TTL) don't expire.
            org.bukkit.Bukkit.getScheduler().runTaskTimer(this,
                    this::publishHostedGames, 20L * 20L, 20L * 20L);
            getServer().getPluginManager().registerEvents(
                    new me.playbosswar.tea.core.impl.coordination.LocalEventMirror(coordinationBackend), this);
            this.arenaAdmin = new me.playbosswar.tea.core.impl.admin.ArenaAdmin(this);
            getServer().getPluginManager().registerEvents(arenaAdmin, this);
            new me.playbosswar.tea.core.impl.admin.AdminMenus(this, arenaAdmin, gameDisplay).register(menuService);
            for (String file : List.of("admin", "admin-arenas", "admin-arena", "admin-players", "admin-end",
                    "admin-move", "admin-maps", "admin-map-versions")) {
                menuService.registerMenu(this, "menus/" + file + ".yml");
            }
            this.settingsService = new me.playbosswar.tea.core.impl.settings.SettingsServiceImpl(
                    this, coordinationBackend, getLogger());
            settingsService.start();
            settingsService.setStore(settingsStore);
            if (sharedFiles != null) {
                coordinationBackend.subscribeEvents(this::onSharedFilesEvent);
            }
            settingsService.register(this, "settings/teacore.yml", paths -> reload());
            settingsService.register(this, "settings/quests.yml", paths -> {
                if (quests != null) {
                    quests.load();
                }
            });
            new me.playbosswar.tea.core.impl.settings.SettingsMenus(this, settingsService).register(menuService);
            for (String file : List.of("admin-settings", "admin-settings-file", "admin-settings-fields",
                    "admin-settings-entries", "admin-settings-entry")) {
                menuService.registerMenu(this, "menus/" + file + ".yml");
            }
            new me.playbosswar.tea.core.impl.coordination.ControlExecutor(
                    this, coordinationBackend, getLogger()).start();
            // Routes a queued player on join after a proxy hop instead of waiting for the next tick.
            getServer().getPluginManager().registerEvents(this.matchmaker, this);
            this.matchmaker.start();

            this.queueBossbarService = new QueueBossbarService(this, coordinationBackend, messageService, audiences);
            getServer().getPluginManager().registerEvents(queueBossbarService, this);
            this.queueBossbarService.start();

            PluginCommand teaCommand = getCommand("tea");
            if (teaCommand != null) {
                TeaCommand exec = new TeaCommand(this);
                teaCommand.setExecutor(exec);
                teaCommand.setTabCompleter(exec);
                PluginCommand dashboardCommand = getCommand("dashboard");
                if (dashboardCommand != null) {
                    dashboardCommand.setExecutor((sender, cmd, label, args) -> {
                        exec.dashboardLogin(sender);
                        return true;
                    });
                }
            }
            // /chat is meaningless on a lobby; enableLobbyFeatures() unregisters it there.
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

            if (lobbyEnabled) {
                enableLobbyFeatures();
            }

            TeaCoreAPI.set(this);
            getLogger().info("TeaCore enabled (v" + getDescription().getVersion() + ")");
            // First tick, after the server finished starting.
            getServer().getScheduler().runTask(this, () -> discordEvents.serverOnline(coordinationBackend.serverId()));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "TeaCore failed to start", e);
            ERROR_TRACKER.trackError(e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (cloudIntegration != null) {
            cloudIntegration.stop();
        }
        if (npcService != null) {
            npcService.shutdown();
        }
        if (discordEvents != null && coordinationBackend != null) {
            discordEvents.serverOffline(coordinationBackend.serverId());
        }
        if (discordWebhooks != null) {
            discordWebhooks.close(); // waits up to 5 s for queued posts
        }
        TeaCoreAPI.set(null);
        if (serverStats != null) {
            serverStats.stop();
        }
        if (spectatorTools != null) {
            spectatorTools.stop();
        }
        if (ratings != null) {
            ratings.close();
        }
        if (quests != null) {
            quests.close();
        }
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
            if (mapEditorTools != null)
                mapEditorTools.stop();
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
                arenaManager.destroySpares();
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

    private void enableLobbyFeatures() {
        if (lobbyFeaturesActive) return;
        // /chat is meaningless on a lobby; hide it from tab-complete and /help.
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
        // Dispatched via /tea lobby (see TeaCommand).
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
        if (quests != null) {
            quests.load();
        }
        messageService.replaceTheme(theme);
        messageService.replaceBundles(loadAllBundles());

        // Enabling at reload is safe (idempotent). Disabling needs a restart because
        // listeners and command executors can't be cleanly torn down, so only warn.
        boolean lobbyEnabled = getConfig().getBoolean("lobby.enabled", false);
        if (lobbyEnabled && !lobbyFeaturesActive) {
            enableLobbyFeatures();
        } else if (!lobbyEnabled && lobbyFeaturesActive) {
            getLogger().warning(
                    "lobby.enabled was turned off, but lobby features can't be torn down at runtime. "
                            + "Restart the server to fully disable them.");
        }
    }

    public void reloadMaps() {
        if (mapService == null || mapEditingService == null)
            return;
        if (sharedFiles != null) {
            sharedFiles.pull();
        }
        reloadMapsFromDisk();
    }

    private void reloadMapsFromDisk() {
        for (var def : List.copyOf(mapService.all())) {
            mapService.unregister(def.gameId(), def.mapId());
        }
        mapEditingService.loadAllAndRegister();
    }

    public me.playbosswar.tea.core.impl.dashboard.auth.LoginCodes dashboardLoginCodes() {
        return dashboardLoginCodes;
    }

    public CoreConfig.DashboardConfig dashboardConfig() {
        return config.dashboard();
    }

    public me.playbosswar.tea.core.impl.discord.DiscordEvents discordEvents() {
        return discordEvents;
    }

    public me.playbosswar.tea.core.impl.admin.ArenaAdmin arenaAdmin() {
        return arenaAdmin;
    }

    public MapEditingService mapEditingService() {
        return mapEditingService;
    }

    public ScoreboardManager scoreboards() {
        return scoreboardManager;
    }

    /** Null when map-setup-scoreboard.enabled is false. */
    public MapSetupSidebar mapSetupSidebar() {
        return mapSetupSidebar;
    }

    /** Null when lobby.enabled is false. */
    public me.playbosswar.tea.core.impl.lobby.command.TeaLobbyCommand lobbyAdminCommand() {
        return lobbyAdminCommand;
    }

    private void publishHostedGames() {
        if (coordinationBackend == null || gameRegistry == null)
            return;
        java.util.Set<me.playbosswar.tea.core.api.game.GameId> ids = new java.util.HashSet<>();
        for (var def : gameRegistry.all()) {
            if (hosting == null || hosting.hosts(def.id())) {
                ids.add(def.id());
            }
        }
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

    public me.playbosswar.tea.core.impl.menu.CoreMenus coreMenus() {
        return coreMenus;
    }

    public CoreStatsService coreStats() {
        return coreStatsService;
    }

    @Override
    public me.playbosswar.tea.core.api.history.MatchHistoryService matchHistory() {
        return matchHistory;
    }

    private me.playbosswar.tea.core.impl.settings.SettingsServiceImpl settingsService;
    private me.playbosswar.tea.core.impl.settings.SettingsStore settingsStore;
    private me.playbosswar.tea.core.impl.arena.Hosting hosting;
    private me.playbosswar.tea.core.impl.map.SharedFiles sharedFiles;

    /** Null when {@code maps.storage: files}. */
    public me.playbosswar.tea.core.impl.map.SharedFiles sharedFiles() {
        return sharedFiles;
    }

    private void pushSharedFiles() {
        var files = sharedFiles;
        if (files == null || !isEnabled()) {
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (files.push() && coordinationBackend != null) {
                coordinationBackend.publishEvent(new me.playbosswar.tea.core.api.coordination.CoordinationEvent(
                        me.playbosswar.tea.core.api.coordination.CoordinationEvent.Type.MAPS_CHANGED,
                        coordinationBackend.serverId(), null, null, null, null, null, null, null, null, null,
                        0, -1, null));
            }
        });
    }

    private void onSharedFilesEvent(me.playbosswar.tea.core.api.coordination.CoordinationEvent e) {
        if (e.type() != me.playbosswar.tea.core.api.coordination.CoordinationEvent.Type.MAPS_CHANGED
                || e.serverId().equals(coordinationBackend.serverId()) || !isEnabled()) {
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (sharedFiles.pull().isEmpty()) {
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                getLogger().info("Maps or waiting rooms changed on " + e.serverId() + "; reloaded them.");
                reloadMapsFromDisk();
                if (arenaManager != null) {
                    arenaManager.waitingRooms().load();
                }
            });
        });
    }

    @Override
    public me.playbosswar.tea.core.api.settings.SettingsService settings() {
        return settingsService;
    }

    public me.playbosswar.tea.core.impl.settings.SettingsServiceImpl settingsService() {
        return settingsService;
    }

    @Override
    public me.playbosswar.tea.core.api.menu.MenuService menus() {
        return menuService;
    }

    private me.playbosswar.tea.core.impl.npc.NpcServiceImpl npcService;
    private me.playbosswar.tea.core.api.platform.Platform platform;

    @Override
    public me.playbosswar.tea.core.api.platform.Platform platform() {
        if (platform == null) {
            platform = me.playbosswar.tea.core.impl.platform.Platforms.load(this);
        }
        return platform;
    }

    @Override
    public me.playbosswar.tea.core.api.npc.NpcService npcs() {
        return npcService;
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
        me.playbosswar.tea.core.api.util.ConfigSync.mergeMissingKeys(this);
        me.playbosswar.tea.core.api.util.ConfigSync.mergeMissingKeys(this, "theme.yml",
                getDataFolder().toPath().resolve("theme.yml"));
        me.playbosswar.tea.core.api.util.ConfigSync.mergeMissingKeys(this, "sidebar.yml",
                getDataFolder().toPath().resolve("sidebar.yml"));
    }

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

    private me.playbosswar.tea.core.impl.quest.QuestService quests;

    public me.playbosswar.tea.core.impl.quest.QuestService quests() {
        return quests;
    }

    private void registerQuests() {
        quests = new me.playbosswar.tea.core.impl.quest.QuestService(this, this, databaseService.dataSource(),
                getLogger());
        quests.load();
        getServer().getPluginManager().registerEvents(quests, this);
        quests.register(menuService);
        menuService.registerMenu(this, "menus/quests.yml");
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            quests.load(p.getUniqueId());
        }
        getServer().getScheduler().runTaskTimerAsynchronously(this, quests::cleanUp, 20L * 60, 20L * 60 * 60 * 6);
        PluginCommand command = getCommand("quests");
        if (command == null) {
            return;
        }
        command.setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                messageService.sendToSender(sender, "core.command.player-only");
                return true;
            }
            if (!quests.enabled()) {
                messageService.send(player, "core.quests.off");
                return true;
            }
            if (menuService.open(player, "quests", java.util.Map.of())) {
                return true;
            }
            for (boolean weekly : new boolean[] {false, true}) {
                for (var q : quests.questsOf(player.getUniqueId(), weekly)) {
                    messageService.send(player, "core.quests.line", java.util.Map.of(
                            "quest", q.name(), "progress", String.valueOf(quests.progressOf(player.getUniqueId(), q)),
                            "amount", String.valueOf(q.amount()), "reward", quests.rewardText(q),
                            "period", weekly ? "weekly" : "daily"));
                }
            }
            return true;
        });
    }

    private void registerLeaderboard() {
        menuService.registerMenu(this, "menus/leaderboard.yml");
        menuService.registerSource("leaderboard", (viewer, args) -> {
            String game = args.get("game");
            if (game == null || game.isBlank()) {
                return List.of();
            }
            List<me.playbosswar.tea.core.api.menu.MenuEntry> out = new java.util.ArrayList<>();
            for (var row : ratings.leaderboard(me.playbosswar.tea.core.api.game.GameId.of(game), 45)) {
                out.add(me.playbosswar.tea.core.api.menu.MenuEntry.of(java.util.Map.of(
                        "rank", String.valueOf(row.rank()), "player", row.name(), "uuid", row.uuid().toString(),
                        "rating", String.valueOf(row.rating()), "games", String.valueOf(row.games()),
                        "wins", String.valueOf(row.wins()), "losses", String.valueOf(row.losses()))));
            }
            return out;
        });
        PluginCommand command = getCommand("leaderboard");
        if (command == null) {
            return;
        }
        command.setExecutor((sender, cmd, label, args) -> {
            if (args.length < 1) {
                messageService.sendToSender(sender, "core.ranked.leaderboard.usage");
                return true;
            }
            me.playbosswar.tea.core.api.game.GameId game;
            try {
                game = me.playbosswar.tea.core.api.game.GameId.of(args[0].toLowerCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                messageService.sendToSender(sender, "core.ranked.leaderboard.usage");
                return true;
            }
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                var rows = ratings.leaderboard(game, 45);
                getServer().getScheduler().runTask(this, () -> {
                    String name = arenaValues.gameName(messageService.defaultLocale(), game);
                    if (sender instanceof org.bukkit.entity.Player p && menuService.open(p, "leaderboard",
                            java.util.Map.of("game", game.value(), "game_name", name))) {
                        return;
                    }
                    if (rows.isEmpty()) {
                        messageService.sendToSender(sender, "core.ranked.leaderboard.empty",
                                java.util.Map.of("game", name));
                        return;
                    }
                    messageService.sendToSender(sender, "core.ranked.leaderboard.header", java.util.Map.of("game", name));
                    for (var row : rows.subList(0, Math.min(10, rows.size()))) {
                        messageService.sendToSender(sender, "core.ranked.leaderboard.line", java.util.Map.of(
                                "rank", String.valueOf(row.rank()), "player", row.name(),
                                "rating", String.valueOf(row.rating()), "wins", String.valueOf(row.wins()),
                                "losses", String.valueOf(row.losses())));
                    }
                });
            });
            return true;
        });
        command.setTabCompleter((sender, cmd, label, args) -> args.length != 1 ? List.of()
                : coordinationBackend.allHostedGames().stream().map(g -> g.value())
                        .filter(v -> v.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).sorted().toList());
    }

    private void setUpBedrockForms() {
        String simulate = System.getProperty("tea.bedrock.simulate", "");
        if (!simulate.isBlank()) {
            me.playbosswar.tea.core.impl.menu.bedrock.SimulatedBridge bridge =
                    new me.playbosswar.tea.core.impl.menu.bedrock.SimulatedBridge(this, simulate);
            getServer().getPluginManager().registerEvents(bridge, this);
            menuService.setBedrock(bridge);
            getLogger().info("Bedrock forms: simulated for " + simulate);
        } else if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            try {
                menuService.setBedrock(new me.playbosswar.tea.core.impl.menu.bedrock.FloodgateBridge(this));
                getLogger().info("Bedrock forms: on (Floodgate found)");
            } catch (LinkageError e) {
                getLogger().warning("Floodgate found but its API could not be loaded; Bedrock players get chest menus");
            }
        }
    }
}
