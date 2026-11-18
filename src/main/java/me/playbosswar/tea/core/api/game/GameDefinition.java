package me.playbosswar.tea.core.api.game;

import java.util.List;
import java.util.Objects;
import me.playbosswar.tea.core.api.team.TeamDefinition;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable description of a registered game type.
 *
 * <p>Player bounds are intentionally not part of a game definition - they live on each
 * {@link me.playbosswar.tea.core.api.map.MapDefinition} so different maps can have different
 * player capacities. Games are registered via
 * {@link GameRegistry#register(GameDefinition)} and typically built with the fluent
 * {@link Builder}.
 *
 * @param id             stable identifier for this game; never {@code null}.
 * @param plugin         owning Bukkit plugin; never {@code null}.
 * @param displayNameKey translation key rendered as the game's human-readable name;
 *                       never {@code null}.
 * @param teamMode       how the game splits participants into teams; never {@code null}.
 * @param teams          declared teams when {@code teamMode == TEAMS}; defensively copied,
 *                       never {@code null}. May be empty for {@link TeamMode#FFA}.
 * @param lifecycle      phase durations and countdown configuration; never {@code null}.
 * @param mapSelection   policy used at allocation time to pick a map; never {@code null}.
 * @param pool           warm-pool sizing for this game's arenas; never {@code null}.
 * @param endMode        what to do with an arena after a match ends; never {@code null}.
 * @param arenaWorld     world-setup and gamemode preferences applied when arenas of
 *                       this game are allocated and when players join/leave; never
 *                       {@code null}.
 * @param icon           item shown for the game in menus (game selector, stats);
 *                       defaults to {@link Material#PAPER}.
 */
public record GameDefinition(
        GameId id,
        Plugin plugin,
        String displayNameKey,
        TeamMode teamMode,
        List<TeamDefinition> teams,
        LifecycleConfig lifecycle,
        MapSelectionPolicy mapSelection,
        PoolConfig pool,
        EndMode endMode,
        ArenaWorldConfig arenaWorld,
        Material icon) {

    /**
     * Canonical constructor that validates required fields and defensively copies
     * {@code teams}.
     *
     * @throws NullPointerException     if any required field is {@code null}.
     * @throws IllegalArgumentException if {@code teamMode} is {@link TeamMode#TEAMS} but
     *                                  {@code teams} is empty.
     */
    public GameDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(teamMode, "teamMode");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(mapSelection, "mapSelection");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(endMode, "endMode");
        Objects.requireNonNull(arenaWorld, "arenaWorld");
        icon = icon == null ? Material.PAPER : icon;
        teams = teams == null ? List.of() : List.copyOf(teams);
        if (teamMode == TeamMode.TEAMS && teams.isEmpty()) {
            throw new IllegalArgumentException("TEAMS mode requires at least one team declared");
        }
    }

    /**
     * Begins building a game definition with sensible defaults.
     *
     * <p>{@code displayNameKey} defaults to {@code "<id>.name"}, {@code teamMode} to
     * {@link TeamMode#FFA}, and the remaining policies to their static factory defaults.
     *
     * @param id     game id; must not be {@code null}.
     * @param plugin owning plugin; must not be {@code null}.
     * @return a new builder, never {@code null}.
     */
    public static Builder builder(GameId id, Plugin plugin) {
        return new Builder(id, plugin);
    }

    /**
     * Fluent builder for {@link GameDefinition}.
     *
     * <p>All setters return the same builder instance. Call {@link #build()} to produce the
     * immutable definition.
     */
    public static final class Builder {
        private final GameId id;
        private final Plugin plugin;
        private String displayNameKey;
        private TeamMode teamMode = TeamMode.FFA;
        private List<TeamDefinition> teams = List.of();
        private LifecycleConfig lifecycle = LifecycleConfig.defaults();
        private MapSelectionPolicy mapSelection = MapSelectionPolicy.random();
        private PoolConfig pool = PoolConfig.defaults();
        private EndMode endMode = EndMode.RESET;
        private ArenaWorldConfig arenaWorld = ArenaWorldConfig.defaults().build();
        private Material icon = Material.PAPER;

        private Builder(GameId id, Plugin plugin) {
            this.id = id;
            this.plugin = plugin;
            this.displayNameKey = id.value() + ".name";
        }

        /**
         * Sets the translation key used as the game's display name.
         *
         * @param key translation key; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder displayNameKey(String key) {
            this.displayNameKey = key;
            return this;
        }

        /**
         * Sets the team mode.
         *
         * @param v new team mode; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder teamMode(TeamMode v) {
            this.teamMode = v;
            return this;
        }

        /**
         * Sets the declared teams.
         *
         * @param v teams to register, or {@code null} to clear.
         * @return this builder, for chaining.
         */
        public Builder teams(@Nullable List<TeamDefinition> v) {
            this.teams = v == null ? List.of() : List.copyOf(v);
            return this;
        }

        /**
         * Sets the lifecycle phase configuration.
         *
         * @param v lifecycle configuration; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder lifecycle(LifecycleConfig v) {
            this.lifecycle = v;
            return this;
        }

        /**
         * Sets the map-selection policy.
         *
         * @param v map selection policy; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder mapSelection(MapSelectionPolicy v) {
            this.mapSelection = v;
            return this;
        }

        /**
         * Sets the warm-pool configuration.
         *
         * @param v pool configuration; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder pool(PoolConfig v) {
            this.pool = v;
            return this;
        }

        /**
         * Sets the end-of-match behavior.
         *
         * @param v end mode; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder endMode(EndMode v) {
            this.endMode = v;
            return this;
        }

        /**
         * Sets the arena-world configuration (world rules, difficulty,
         * gamemodes, time/weather).
         *
         * @param v arena-world configuration; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder arenaWorld(ArenaWorldConfig v) {
            this.arenaWorld = v;
            return this;
        }

        /**
         * Sets the item shown for this game in menus such as the game selector.
         *
         * @param v the icon material; {@code null} means {@link Material#PAPER}.
         * @return this builder, for chaining.
         */
        public Builder icon(Material v) {
            this.icon = v;
            return this;
        }

        /**
         * Builds the immutable definition.
         *
         * @return a new {@link GameDefinition}, never {@code null}.
         * @throws IllegalArgumentException if the configured state is invalid (see the
         *                                  canonical constructor).
         */
        public GameDefinition build() {
            return new GameDefinition(id, plugin, displayNameKey,
                    teamMode, teams, lifecycle, mapSelection, pool, endMode, arenaWorld, icon);
        }
    }
}
