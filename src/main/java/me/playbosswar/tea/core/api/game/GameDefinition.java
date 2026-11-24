package me.playbosswar.tea.core.api.game;

import java.util.List;
import java.util.Objects;
import me.playbosswar.tea.core.api.team.TeamDefinition;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable description of a registered game type, usually built with {@link Builder} and
 * registered via {@link GameRegistry#register(GameDefinition)}.
 *
 * <p>Player bounds live on each {@link me.playbosswar.tea.core.api.map.MapDefinition}, not
 * here, so maps of the same game can have different capacities.
 *
 * @param displayNameKey translation key for the game's name.
 * @param teams          declared teams; required for {@link TeamMode#TEAMS}, may be empty
 *                       for {@link TeamMode#FFA}. Defensively copied.
 * @param mapSelection   picks a map at allocation time.
 * @param endMode        what happens to an arena after a match ends.
 * @param arenaWorld     world rules and gamemodes applied when arenas are allocated and
 *                       players join or leave.
 * @param icon           menu item for the game; defaults to {@link Material#PAPER}.
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
     * @throws IllegalArgumentException if {@code teamMode} is {@link TeamMode#TEAMS} and
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
     * Defaults: {@code displayNameKey} is {@code "<id>.name"}, {@code teamMode} is
     * {@link TeamMode#FFA}, and the other policies use their static factory defaults.
     */
    public static Builder builder(GameId id, Plugin plugin) {
        return new Builder(id, plugin);
    }

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

        public Builder displayNameKey(String key) {
            this.displayNameKey = key;
            return this;
        }

        public Builder teamMode(TeamMode v) {
            this.teamMode = v;
            return this;
        }

        /** {@code null} clears the teams. */
        public Builder teams(@Nullable List<TeamDefinition> v) {
            this.teams = v == null ? List.of() : List.copyOf(v);
            return this;
        }

        public Builder lifecycle(LifecycleConfig v) {
            this.lifecycle = v;
            return this;
        }

        public Builder mapSelection(MapSelectionPolicy v) {
            this.mapSelection = v;
            return this;
        }

        public Builder pool(PoolConfig v) {
            this.pool = v;
            return this;
        }

        public Builder endMode(EndMode v) {
            this.endMode = v;
            return this;
        }

        public Builder arenaWorld(ArenaWorldConfig v) {
            this.arenaWorld = v;
            return this;
        }

        /** {@code null} means {@link Material#PAPER}. */
        public Builder icon(Material v) {
            this.icon = v;
            return this;
        }

        /** @throws IllegalArgumentException if TEAMS mode has no teams. */
        public GameDefinition build() {
            return new GameDefinition(id, plugin, displayNameKey,
                    teamMode, teams, lifecycle, mapSelection, pool, endMode, arenaWorld, icon);
        }
    }
}
