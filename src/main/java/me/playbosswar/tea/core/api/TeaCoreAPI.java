package me.playbosswar.tea.core.api;

import me.playbosswar.tea.core.api.achievement.AchievementService;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.chat.ChatRouter;
import me.playbosswar.tea.core.api.chat.MatchBroadcaster;
import me.playbosswar.tea.core.api.coordination.CoordinationBackend;
import me.playbosswar.tea.core.api.cooldown.CooldownService;
import me.playbosswar.tea.core.api.economy.EconomyService;
import me.playbosswar.tea.core.api.effect.EffectService;
import me.playbosswar.tea.core.api.game.GameRegistry;
import me.playbosswar.tea.core.api.kit.KitRegistry;
import me.playbosswar.tea.core.api.map.MapService;
import me.playbosswar.tea.core.api.message.MessageService;
import me.playbosswar.tea.core.api.persistence.DatabaseService;
import me.playbosswar.tea.core.api.placeholder.PlaceholderService;
import me.playbosswar.tea.core.api.profile.PlayerProfileService;
import me.playbosswar.tea.core.api.stats.StatsRegistry;
import me.playbosswar.tea.core.api.team.TeamManager;
import me.playbosswar.tea.core.api.theme.Theme;
import me.playbosswar.tea.core.api.transport.ProxyTransport;
import me.playbosswar.tea.core.api.version.VersionRequirement;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point for the TeaCore engine. Obtain it with {@link #get()} once
 * TeaCore has enabled, e.g. in {@code onEnable} of a plugin that depends on it.
 */
public interface TeaCoreAPI {

    String version();

    /**
     * @param requirement a {@link VersionRequirement} spec, e.g. {@code ">=0.2.0"}
     * @throws IllegalArgumentException if the spec cannot be parsed
     */
    default boolean isCompatible(String requirement) {
        return VersionRequirement.parse(requirement).isSatisfiedBy(version());
    }

    /**
     * Disables {@code plugin} and logs why when the running TeaCore does not
     * satisfy {@code requirement}. Call it first in {@code onEnable}:
     *
     * <pre>{@code
     * if (!core.requireVersion(this, ">=0.2.0")) return;
     * }</pre>
     *
     * @return {@code false} if the plugin was disabled
     */
    default boolean requireVersion(org.bukkit.plugin.Plugin plugin, String requirement) {
        if (isCompatible(requirement)) {
            return true;
        }
        plugin.getLogger().severe("Requires TeaCore " + requirement + " but found " + version()
                + " - disabling " + plugin.getName() + ". Update TeaCore or this plugin so their versions match.");
        plugin.getServer().getPluginManager().disablePlugin(plugin);
        return false;
    }

    /** This server's id on the network (e.g. {@code "arena-1"}); owns its arena snapshots and queues. */
    String serverId();

    /** {@code true} when {@code network.mode} is {@code standalone} (no proxy). */
    boolean isStandalone();

    /** Proxy-side name of the lobby server that players are sent to at match end. */
    String lobbyServerName();

    MessageService messageService();

    Theme theme();

    DatabaseService database();

    PlayerProfileService playerProfiles();

    GameRegistry games();

    MapService maps();

    ArenaManager arenas();

    TeamManager teams();

    KitRegistry kits();

    CooldownService cooldowns();

    EffectService effects();

    ChatRouter chatRouter();

    MatchBroadcaster matchBroadcaster();

    /** Shares arena, queue and player state across the network. */
    CoordinationBackend coordination();

    ProxyTransport proxyTransport();

    StatsRegistry stats();

    /**
     * Vault economy wrapper. Check {@link EconomyService#isAvailable()} first:
     * no economy plugin may have registered with Vault yet.
     */
    EconomyService economy();

    /** Backs the {@code %tea_...%} PlaceholderAPI expansion; games register their own placeholders here. */
    PlaceholderService placeholders();

    AchievementService achievements();

    /**
     * Damage history and kill credit (killer and assists). Games that fake
     * deaths must report them here so core stats and
     * {@link me.playbosswar.tea.core.api.combat.ArenaKillEvent} stay correct.
     */
    me.playbosswar.tea.core.api.combat.CombatTracker combat();

    me.playbosswar.tea.core.api.loot.LootService loot();

    /** Feature switches from {@code features:} in config.yml. */
    me.playbosswar.tea.core.api.feature.FeatureService features();

    me.playbosswar.tea.core.api.sidebar.SidebarService sidebars();

    me.playbosswar.tea.core.api.menu.MenuService menus();

    /** Right-click NPCs that open menus, placed per map with {@code /tea map <npc id>}. */
    me.playbosswar.tea.core.api.npc.NpcService npcs();

    /**
     * Version-independent access to API that differs between 1.8.8 and the
     * latest Minecraft. Use it instead of version-specific Bukkit calls.
     */
    me.playbosswar.tea.core.api.platform.Platform platform();

    me.playbosswar.tea.core.api.history.MatchHistoryService matchHistory();

    /** Settings editable from {@code /tea admin} and the dashboard. */
    me.playbosswar.tea.core.api.settings.SettingsService settings();

    /** @throws IllegalStateException if TeaCore has not been initialized yet */
    static TeaCoreAPI get() {
        TeaCoreAPI instance = Holder.INSTANCE;
        if (instance == null) {
            throw new IllegalStateException("TeaCore has not been initialized yet.");
        }
        return instance;
    }

    /** Called by TeaCore on enable and disable. Not for game plugins. */
    static void set(@Nullable TeaCoreAPI instance) {
        Holder.INSTANCE = instance;
    }

    final class Holder {
        private static volatile TeaCoreAPI INSTANCE;

        private Holder() {
        }
    }
}
