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
 * Top-level entry point for the TeaCore engine.
 *
 * <p>
 * Exposes every registered subsystem (arenas, games, chat, coordination,
 * persistence, etc.)
 * behind a single interface. The singleton instance is populated by
 * {@code TeaCorePlugin} during
 * {@code onEnable} and retrieved by game plugins via {@link #get()}.
 *
 * @apiNote Call {@link #get()} only after TeaCore has finished enabling; a safe
 *          place is
 *          {@code onEnable} of a plugin that declares TeaCore as a dependency.
 */
public interface TeaCoreAPI {

    /**
     * Returns the running TeaCore version string.
     *
     * @return the version, never {@code null}.
     */
    String version();

    /**
     * Tests whether the running TeaCore satisfies a version requirement.
     *
     * @param requirement a {@link VersionRequirement} spec, e.g. {@code ">=0.2.0"}
     *                    or {@code "==1.0.0"}; must not be {@code null} or blank.
     * @return {@code true} if {@link #version()} satisfies the requirement.
     * @throws IllegalArgumentException if the spec cannot be parsed.
     */
    default boolean isCompatible(String requirement) {
        return VersionRequirement.parse(requirement).isSatisfiedBy(version());
    }

    /**
     * Enforces a TeaCore version requirement for a dependent plugin (typically a
     * game). When the running TeaCore does not satisfy {@code requirement}, this
     * logs an explanatory error through the plugin's logger and disables the
     * plugin so it does not start against an incompatible core. Call it at the
     * top of {@code onEnable} and return early when it returns {@code false}:
     *
     * <pre>{@code
     * if (!core.requireVersion(this, ">=0.2.0")) return;
     * }</pre>
     *
     * @param plugin      the dependent plugin to disable on mismatch; must not be
     *                    {@code null}.
     * @param requirement a {@link VersionRequirement} spec; must not be
     *                    {@code null} or blank.
     * @return {@code true} if compatible (the caller may continue), {@code false}
     *         if the plugin was disabled.
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

    /**
     * Returns the unique identifier for this backend server.
     *
     * <p>
     * Used by the coordination backend to attribute arena snapshots and queue
     * ownership
     * across the network (e.g. {@code "arena-1"}).
     *
     * @return the server id, never {@code null}.
     */
    String serverId();

    /**
     * Indicates whether TeaCore is running without a proxy.
     *
     * @return {@code true} when configured with {@code network.mode: standalone},
     *         {@code false} when participating in a proxied network.
     */
    boolean isStandalone();

    /**
     * Returns the proxy-side name of the lobby backend.
     *
     * <p>
     * Used to send players back to the lobby at match end via
     * {@link ProxyTransport}.
     *
     * @return the lobby server name as configured on the proxy, never {@code null}.
     */
    String lobbyServerName();

    /**
     * Returns the message service for rendering and dispatching localized, themed
     * messages.
     *
     * @return the service handle, never {@code null}.
     */
    MessageService messageService();

    /**
     * Returns the active UI theme.
     *
     * @return the theme, never {@code null}.
     */
    Theme theme();

    /**
     * Returns the shared database service.
     *
     * @return the database handle, never {@code null}.
     */
    DatabaseService database();

    /**
     * Returns the player-profile service.
     *
     * @return the profile service, never {@code null}.
     */
    PlayerProfileService playerProfiles();

    /**
     * Returns the game registry.
     *
     * @return the registry, never {@code null}.
     */
    GameRegistry games();

    /**
     * Returns the map service.
     *
     * @return the service, never {@code null}.
     */
    MapService maps();

    /**
     * Returns the arena manager.
     *
     * @return the manager, never {@code null}.
     */
    ArenaManager arenas();

    /**
     * Returns the team manager.
     *
     * @return the manager, never {@code null}.
     */
    TeamManager teams();

    /**
     * Returns the kit registry.
     *
     * @return the registry, never {@code null}.
     */
    KitRegistry kits();

    /**
     * Returns the cooldown service.
     *
     * @return the service, never {@code null}.
     */
    CooldownService cooldowns();

    /**
     * Returns the effect service.
     *
     * @return the service, never {@code null}.
     */
    EffectService effects();

    /**
     * Returns the chat router.
     *
     * @return the router, never {@code null}.
     */
    ChatRouter chatRouter();

    /**
     * Returns the match broadcaster for arena- and team-scoped messages.
     *
     * @return the broadcaster, never {@code null}.
     */
    MatchBroadcaster matchBroadcaster();

    /**
     * Returns the coordination backend used to share state across the Tea network.
     *
     * @return the backend, never {@code null}.
     */
    CoordinationBackend coordination();

    /**
     * Returns the proxy transport used to move players between backend servers.
     *
     * @return the transport, never {@code null}.
     */
    ProxyTransport proxyTransport();

    /**
     * Returns the stats registry aggregating per-game stat providers.
     *
     * @return the registry, never {@code null}.
     */
    StatsRegistry stats();

    /**
     * Returns the economy service, which wraps the server's Vault economy
     * provider.
     *
     * <p>
     * Callers should check {@link EconomyService#isAvailable()} before issuing
     * balance-dependent operations — Vault is a hard dependency of TeaCore, but
     * a concrete economy plugin may not have registered itself with Vault yet.
     *
     * @return the economy service, never {@code null}.
     */
    EconomyService economy();

    /**
     * Returns the placeholder service backing the {@code %tea_...%}
     * PlaceholderAPI expansion. Games register their own placeholders here.
     *
     * @return the placeholder service, never {@code null}.
     */
    PlaceholderService placeholders();

    /**
     * Returns the shared achievement service. Games register their
     * {@link me.playbosswar.tea.core.api.achievement.AchievementDefinition}s on
     * enable and award them by id during play.
     *
     * @return the achievement service, never {@code null}.
     */
    AchievementService achievements();

    /**
     * Returns the initialized singleton instance.
     *
     * @return the running TeaCore API, never {@code null}.
     * @throws IllegalStateException if TeaCore has not yet been initialized.
     */
    static TeaCoreAPI get() {
        TeaCoreAPI instance = Holder.INSTANCE;
        if (instance == null) {
            throw new IllegalStateException("TeaCore has not been initialized yet.");
        }
        return instance;
    }

    /**
     * Installs the singleton instance.
     *
     * <p>
     * Called by {@code TeaCorePlugin#onEnable()}. Not intended for use by game
     * plugins.
     *
     * @param instance the API implementation to publish, or {@code null} to clear.
     */
    static void set(@Nullable TeaCoreAPI instance) {
        Holder.INSTANCE = instance;
    }

    /**
     * Internal holder for the {@link TeaCoreAPI} singleton.
     *
     * <p>
     * Kept as a nested class so the field initialization happens lazily on first
     * access
     * and the instance is safely published via the {@code volatile} modifier.
     */
    final class Holder {
        private static volatile TeaCoreAPI INSTANCE;

        private Holder() {
        }
    }
}
