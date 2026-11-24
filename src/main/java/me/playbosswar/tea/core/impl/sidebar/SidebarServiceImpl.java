package me.playbosswar.tea.core.impl.sidebar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.feature.FeatureService;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.sidebar.SidebarData;
import me.playbosswar.tea.core.api.sidebar.SidebarProvider;
import me.playbosswar.tea.core.api.sidebar.SidebarService;
import me.playbosswar.tea.core.api.team.Team;
import me.playbosswar.tea.core.impl.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SidebarServiceImpl implements SidebarService {

    static final int GAME_PRIORITY = 100;
    static final int LOBBY_PRIORITY = 50;

    private final TeaCoreAPI core;
    private final ScoreboardManager manager;
    private final FeatureService features;
    private final ArenaValues values;
    private final Supplier<ConfigurationSection> lobbyLayout;
    private Supplier<ConfigurationSection> spectatorLayout = () -> null;
    private java.util.function.Predicate<Player> spectatorView = p -> false;

    /** Used when sidebar.yml has no {@code spectator} section. */
    private static final List<String> DEFAULT_SPECTATOR_LINES = List.of(
            "<secondary>Time:</secondary> <yellow><time></yellow>",
            "<muted>You are spectating</muted>",
            "",
            "@teams: <team-name><gray>:</gray> <white><team-score></white>",
            "",
            "<secondary>Watching:</secondary> <highlight><watching></highlight>",
            "<secondary>K/D/A:</secondary> <white><watching-kills>/<watching-deaths>/<watching-assists></white>");

    public void setSpectatorSidebar(Supplier<ConfigurationSection> layout, java.util.function.Predicate<Player> who) {
        this.spectatorLayout = layout;
        this.spectatorView = who;
    }

    private List<String> linesFor(Player viewer, Arena arena, GameLayout game) {
        String phase = phaseOf(arena.state());
        if (("live".equals(phase) || "starting".equals(phase))
                && arena.spectators().contains(viewer.getUniqueId()) && spectatorView.test(viewer)) {
            List<String> own = game.layout().phases().get("spectating");
            if (own != null) {
                return own;
            }
            ConfigurationSection section = spectatorLayout.get();
            if (section == null) {
                return DEFAULT_SPECTATOR_LINES;
            }
            if (section.getBoolean("enabled", true)) {
                return List.copyOf(section.getStringList("lines"));
            }
        }
        return game.layout().lines(phase);
    }
    private final Supplier<Boolean> lobbyEnabled;
    private final Map<GameId, GameLayout> games = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    /** @param lobbyLayout the {@code lobby} section of sidebar.yml, may be null */
    public SidebarServiceImpl(TeaCoreAPI core, ScoreboardManager manager, FeatureService features,
                              ArenaValues values, Supplier<ConfigurationSection> lobbyLayout,
                              Supplier<Boolean> lobbyEnabled) {
        this.core = core;
        this.manager = manager;
        this.features = features;
        this.values = values;
        this.lobbyLayout = lobbyLayout;
        this.lobbyEnabled = lobbyEnabled;
        manager.register(new GameProvider());
        manager.register(new LobbyProvider());
    }

    @Override
    public void registerGame(Plugin owner, GameId game, ConfigurationSection layout, SidebarData data) {
        games.put(game, new GameLayout(Layout.parse(layout), data == null ? SidebarData.NONE : data));
    }

    @Override
    public void unregisterGame(GameId game) {
        games.remove(game);
    }

    @Override
    public void registerProvider(SidebarProvider provider) {
        manager.register(provider);
    }

    @Override
    public void setHidden(Player player, boolean hide) {
        if (hide) {
            hidden.add(player.getUniqueId());
        } else {
            hidden.remove(player.getUniqueId());
        }
        manager.refresh(player);
    }

    @Override
    public boolean isHidden(Player player) {
        return hidden.contains(player.getUniqueId());
    }

    @Override
    public void refresh(Player player) {
        manager.refresh(player);
    }

    public boolean allowed(Player player) {
        return !hidden.contains(player.getUniqueId());
    }

    record Layout(boolean enabled, String title, Map<String, List<String>> phases) {

        static Layout parse(ConfigurationSection section) {
            if (section == null) {
                return new Layout(false, "", Map.of());
            }
            Map<String, List<String>> phases = new HashMap<>();
            for (String key : section.getKeys(false)) {
                if (section.isList(key)) {
                    phases.put(key, List.copyOf(section.getStringList(key)));
                }
            }
            return new Layout(section.getBoolean("enabled", true), section.getString("title", ""), phases);
        }

        List<String> lines(String phase) {
            List<String> lines = phases.get(phase);
            if (lines == null && "starting".equals(phase)) {
                lines = phases.get("countdown");
            }
            if (lines == null) {
                lines = phases.get("live");
            }
            return lines == null ? List.of() : lines;
        }
    }

    private record GameLayout(Layout layout, SidebarData data) { }

    static String phaseOf(ArenaState state) {
        return switch (state) {
            case WAITING, ALLOCATING -> "waiting";
            case COUNTDOWN -> "countdown";
            case STARTING -> "starting";
            case LIVE -> "live";
            default -> "ended";
        };
    }

    private SidebarProvider.Sidebar render(Player viewer, Layout layout, List<String> templates,
                                           Map<String, String> base, Arena arena, SidebarData data) {
        MessageService ms = core.messageService();
        List<String> out = new ArrayList<>();
        for (String template : templates) {
            if (arena != null && template.startsWith("@players:")) {
                String row = template.substring("@players:".length()).stripLeading();
                List<Player> players = new ArrayList<>();
                for (UUID uuid : arena.players()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        players.add(p);
                    }
                }
                for (Player p : data.sortPlayers(arena, players)) {
                    Map<String, String> ph = new HashMap<>(base);
                    ph.putAll(values.playerRow(viewer, arena, p));
                    ph.putAll(data.playerRow(viewer, arena, p));
                    out.add(line(ms, viewer, row, ph));
                }
            } else if (arena != null && template.startsWith("@teams:")) {
                String row = template.substring("@teams:".length()).stripLeading();
                for (Team team : core.teams().teamsFor(arena.id())) {
                    Map<String, String> ph = new HashMap<>(base);
                    ph.putAll(values.teamRow(viewer, arena, team));
                    ph.putAll(data.teamRow(viewer, arena, team.id().value()));
                    out.add(line(ms, viewer, row, ph));
                }
            } else {
                out.add(line(ms, viewer, template, base));
            }
        }
        return new SidebarProvider.Sidebar(line(ms, viewer, layout.title(), base), out);
    }

    private static String line(MessageService ms, Player viewer, String template, Map<String, String> ph) {
        return ms.renderInline(PapiBridge.apply(viewer, template), ph);
    }

    /** Ignores the /board toggle. Used to mirror sidebars to the dashboard. */
    public Optional<SidebarProvider.Sidebar> gameSidebar(Player viewer) {
        Arena arena = core.arenas().arenaOf(viewer.getUniqueId()).orElse(null);
        if (arena == null || !features.enabled("sidebar") || !features.enabled(arena.gameId(), "sidebar")) {
            return Optional.empty();
        }
        GameLayout game = games.get(arena.gameId());
        if (game == null || !game.layout().enabled()) {
            return Optional.empty();
        }
        Map<String, String> base = new HashMap<>(values.forViewer(viewer, arena));
        base.putAll(game.data().placeholders(viewer, arena));
        List<String> lines = linesFor(viewer, arena, game);
        return Optional.of(render(viewer, game.layout(), lines, base, arena, game.data()));
    }

    private final class GameProvider implements SidebarProvider {
        @Override
        public int priority() {
            return GAME_PRIORITY;
        }

        @Override
        public Optional<Sidebar> sidebarFor(Player player) {
            if (!allowed(player)) {
                return Optional.empty();
            }
            Arena arena = core.arenas().arenaOf(player.getUniqueId()).orElse(null);
            if (arena == null || !features.enabled(arena.gameId(), "sidebar")) {
                return Optional.empty();
            }
            GameLayout game = games.get(arena.gameId());
            if (game == null || !game.layout().enabled()) {
                return Optional.empty();
            }
            Map<String, String> base = new HashMap<>(values.forViewer(player, arena));
            base.putAll(game.data().placeholders(player, arena));
            List<String> lines = linesFor(player, arena, game);
            return Optional.of(render(player, game.layout(), lines, base, arena, game.data()));
        }
    }

    private final class LobbyProvider implements SidebarProvider {
        @Override
        public int priority() {
            return LOBBY_PRIORITY;
        }

        @Override
        public Optional<Sidebar> sidebarFor(Player player) {
            if (!allowed(player) || !lobbyEnabled.get() || !features.enabled("sidebar")
                    || !features.enabled("lobby-sidebar")
                    || core.arenas().arenaOf(player.getUniqueId()).isPresent()) {
                return Optional.empty();
            }
            Layout layout = Layout.parse(lobbyLayout.get());
            if (!layout.enabled()) {
                return Optional.empty();
            }
            Map<String, String> base = new HashMap<>();
            base.put("player", player.getName());
            base.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
            return Optional.of(render(player, layout, layout.lines("lines"), base, null, SidebarData.NONE));
        }
    }
}
