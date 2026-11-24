package me.playbosswar.tea.core.api.game;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.jetbrains.annotations.Nullable;

/**
 * World and player-state settings TeaCore applies to every arena of a game.
 * Set once on {@link GameDefinition}, not per map.
 *
 * @param leaveGameMode     gamemode set on a player as they leave the arena.
 * @param allowMonsters     passed to
 *                          {@link org.bukkit.World#setSpawnFlags(boolean, boolean)}.
 * @param allowAnimals      passed to
 *                          {@link org.bukkit.World#setSpawnFlags(boolean, boolean)}.
 * @param timeOfDay         locked world time in ticks ({@code 6000} is noon);
 *                          {@code null} leaves the vanilla cycle running.
 * @param storm             forces rain on or off; {@code null} leaves it alone.
 * @param thundering        forces thunder on or off; {@code null} leaves it alone.
 * @param gameRules         game rule overrides by name, applied last so they win
 *                          on any clash.
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
        Map<String, String> gameRules) {

    public ArenaWorldConfig {
        gameRules = gameRules == null ? Map.of() : Map.copyOf(gameRules);
    }

    /**
     * Builder preset: peaceful, no weather, no mob spawning, no natural
     * regeneration, keep-inventory, time locked at noon, participants in
     * ADVENTURE, spectators in SPECTATOR, leaving players set to SURVIVAL.
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
                .gameRule("doMobSpawning", false)
                .gameRule("doWeatherCycle", false)
                .gameRule("doDaylightCycle", false)
                .gameRule("doImmediateRespawn", true)
                .gameRule("keepInventory", true)
                .gameRule("announceAdvancements", false)
                .gameRule("doInsomnia", false)
                .gameRule("doPatrolSpawning", false)
                .gameRule("doTraderSpawning", false)
                .gameRule("naturalRegeneration", false);
    }

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
        private final Map<String, String> gameRules = new LinkedHashMap<>();

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
         * Sets a game rule by name, e.g. {@code gameRule("doMobSpawning", false)}.
         * Rules the running server version lacks are ignored.
         */
        public Builder gameRule(String rule, Object value) {
            this.gameRules.put(rule, String.valueOf(value));
            return this;
        }

        /** Drops an override so the rule keeps its vanilla value. */
        public Builder clearGameRule(String rule) {
            this.gameRules.remove(rule);
            return this;
        }

        public ArenaWorldConfig build() {
            return new ArenaWorldConfig(playerGameMode, spectatorGameMode, leaveGameMode,
                    difficulty, allowMonsters, allowAnimals,
                    timeOfDay, storm, thundering, gameRules);
        }
    }
}
