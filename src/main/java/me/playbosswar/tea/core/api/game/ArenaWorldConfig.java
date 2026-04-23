package me.playbosswar.tea.core.api.game;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.jetbrains.annotations.Nullable;

/**
 * Per-game configuration for the world and player state TeaCore applies to
 * arenas of this game.
 *
 * <p>
 * Arenas run in dedicated Bukkit worlds, and TeaCore owns the initial
 * configuration of those worlds plus the gamemode players are dropped into on
 * join. Games declare their preferences once on {@link GameDefinition} rather
 * than per-map, because these rules are a trait of the game type (FFA fight
 * vs. build-and-destroy vs. race) rather than a trait of any particular
 * arena. The {@link #defaults()} factory returns the vanilla minigame-friendly
 * preset TeaCore used before this was configurable (peaceful, no weather, no
 * mob spawning, time locked at noon, players enter in ADVENTURE).
 *
 * @param playerGameMode    gamemode applied to participants when they join an
 *                          arena; never {@code null}.
 * @param spectatorGameMode gamemode applied to spectators when they join;
 *                          never {@code null}.
 * @param leaveGameMode     gamemode set on a player as they leave the arena
 *                          (e.g. sent back to lobby on single-server or via
 *                          proxy); never {@code null}.
 * @param difficulty        Bukkit difficulty for the arena world; never
 *                          {@code null}.
 * @param allowMonsters     first argument to
 *                          {@link org.bukkit.World#setSpawnFlags(boolean, boolean)};
 *                          allows vanilla hostile-mob spawning.
 * @param allowAnimals      second argument to
 *                          {@link org.bukkit.World#setSpawnFlags(boolean, boolean)};
 *                          allows vanilla passive-mob spawning.
 * @param timeOfDay         if non-null, locked world time in ticks
 *                          ({@code 0} dawn, {@code 6000} noon, {@code 18000}
 *                          midnight). {@code null} leaves the vanilla cycle
 *                          untouched.
 * @param storm             if non-null, forces rain on ({@code true}) or off
 *                          ({@code false}). {@code null} means "don't touch."
 * @param thundering        if non-null, forces thunder on or off. {@code null}
 *                          means "don't touch."
 * @param gameRules         typed overrides for Bukkit {@link GameRule}s;
 *                          applied after the defaults so {@code gameRules}
 *                          wins on any clash. Defensively copied and never
 *                          {@code null}.
 */
public record ArenaWorldConfig(
        GameMode playerGameMode,
        GameMode spectatorGameMode,
        GameMode leaveGameMode,
        Difficulty difficulty,
        boolean allowMonsters,
        boolean allowAnimals,
        @Nullable Long timeOfDay,
        @Nullable Boolean storm,
        @Nullable Boolean thundering,
        Map<GameRule<?>, Object> gameRules) {

    /**
     * Canonical constructor that defensively copies {@code gameRules},
     * normalizing {@code null} to an empty map.
     */
    public ArenaWorldConfig {
        gameRules = gameRules == null ? Map.of() : Map.copyOf(gameRules);
    }

    /**
     * Returns TeaCore's baseline arena-world preset: peaceful, no weather, no
     * mob spawning, no natural regeneration, time locked at noon, participants
     * in ADVENTURE, spectators in SPECTATOR, leaving players returned to
     * SURVIVAL.
     *
     * @return a fresh builder initialized to the baseline, never {@code null}.
     */
    public static Builder defaults() {
        return new Builder()
                .playerGameMode(GameMode.ADVENTURE)
                .spectatorGameMode(GameMode.SPECTATOR)
                .leaveGameMode(GameMode.SURVIVAL)
                .difficulty(Difficulty.PEACEFUL)
                .allowMonsters(false)
                .allowAnimals(false)
                .timeOfDay(6000L)
                .storm(false)
                .thundering(false)
                .gameRule(GameRule.DO_MOB_SPAWNING, false)
                .gameRule(GameRule.DO_WEATHER_CYCLE, false)
                .gameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                .gameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                .gameRule(GameRule.KEEP_INVENTORY, true)
                .gameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                .gameRule(GameRule.DO_INSOMNIA, false)
                .gameRule(GameRule.DO_PATROL_SPAWNING, false)
                .gameRule(GameRule.DO_TRADER_SPAWNING, false)
                .gameRule(GameRule.NATURAL_REGENERATION, false);
    }

    /**
     * Fluent builder. All setters return {@code this}; call {@link #build()}
     * to freeze into an immutable {@link ArenaWorldConfig}.
     */
    public static final class Builder {
        private GameMode playerGameMode = GameMode.ADVENTURE;
        private GameMode spectatorGameMode = GameMode.SPECTATOR;
        private GameMode leaveGameMode = GameMode.SURVIVAL;
        private Difficulty difficulty = Difficulty.PEACEFUL;
        private boolean allowMonsters = false;
        private boolean allowAnimals = false;
        private @Nullable Long timeOfDay;
        private @Nullable Boolean storm;
        private @Nullable Boolean thundering;
        private final Map<GameRule<?>, Object> gameRules = new LinkedHashMap<>();

        public Builder playerGameMode(GameMode v) { this.playerGameMode = v; return this; }
        public Builder spectatorGameMode(GameMode v) { this.spectatorGameMode = v; return this; }
        public Builder leaveGameMode(GameMode v) { this.leaveGameMode = v; return this; }
        public Builder difficulty(Difficulty v) { this.difficulty = v; return this; }
        public Builder allowMonsters(boolean v) { this.allowMonsters = v; return this; }
        public Builder allowAnimals(boolean v) { this.allowAnimals = v; return this; }
        public Builder timeOfDay(@Nullable Long v) { this.timeOfDay = v; return this; }
        public Builder storm(@Nullable Boolean v) { this.storm = v; return this; }
        public Builder thundering(@Nullable Boolean v) { this.thundering = v; return this; }

        /**
         * Sets a typed Bukkit {@link GameRule}. Existing entries for the same
         * rule are replaced.
         *
         * @param rule  the rule to set; must not be {@code null}.
         * @param value the value, typed by the rule; must not be {@code null}.
         * @param <T>   the rule's value type.
         * @return this builder, for chaining.
         */
        public <T> Builder gameRule(GameRule<T> rule, T value) {
            this.gameRules.put(rule, value);
            return this;
        }

        /**
         * Removes a previously-set {@link GameRule} override, falling back to
         * vanilla defaults for that rule.
         *
         * @param rule the rule to clear; must not be {@code null}.
         * @return this builder, for chaining.
         */
        public Builder clearGameRule(GameRule<?> rule) {
            this.gameRules.remove(rule);
            return this;
        }

        /**
         * Builds the immutable config.
         *
         * @return a new {@link ArenaWorldConfig}, never {@code null}.
         */
        public ArenaWorldConfig build() {
            return new ArenaWorldConfig(playerGameMode, spectatorGameMode, leaveGameMode,
                    difficulty, allowMonsters, allowAnimals,
                    timeOfDay, storm, thundering, gameRules);
        }
    }
}
