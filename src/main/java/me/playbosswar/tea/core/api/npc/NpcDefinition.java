package me.playbosswar.tea.core.api.npc;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * An NPC that stands in a game's arenas and opens a menu when right-clicked,
 * e.g. a shopkeeper. Map authors place them with {@code /tea map <id>} (one NPC
 * per point). Server owners can restyle or relink any NPC under
 * {@code npcs.<game>.<id>} in TeaCore's config.
 *
 * @param id            NPC id, also the {@code /tea map <id>} subcommand
 *                      (e.g. {@code "shop"}). Games using the same id share the
 *                      map points.
 * @param menuId        {@code MenuService} id opened on right-click, or
 *                      {@code null} to only run {@code onClick}.
 * @param onClick       run on right-click when there is no menu or the menu
 *                      can't open (menus turned off), or {@code null}.
 * @param entityType    any living entity type.
 * @param defaultSpots  where to stand on a map that has no points for this
 *                      NPC, or {@code null} for nowhere.
 */
public record NpcDefinition(
        GameId game,
        String id,
        @Nullable String menuId,
        @Nullable Consumer<Player> onClick,
        String nameKey,
        EntityType entityType,
        Set<ArenaState> activeStates,
        @Nullable Function<Arena, List<Location>> defaultSpots) {

    public NpcDefinition {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(entityType, "entityType");
        activeStates = Set.copyOf(activeStates);
        if (activeStates.isEmpty()) {
            throw new IllegalArgumentException("activeStates must not be empty");
        }
    }

    public static Builder builder(GameId game, String id) {
        return new Builder(game, id);
    }

    public static final class Builder {
        private final GameId game;
        private final String id;
        private String menuId;
        private Consumer<Player> onClick;
        private String nameKey;
        private EntityType entityType = EntityType.VILLAGER;
        private Set<ArenaState> activeStates = EnumSet.of(ArenaState.LIVE);
        private Function<Arena, List<Location>> defaultSpots;

        private Builder(GameId game, String id) {
            this.game = game;
            this.id = id;
        }

        public Builder menu(String menuId) { this.menuId = menuId; return this; }

        /** Runs on right-click when there is no menu or it can't open. */
        public Builder onClick(Consumer<Player> onClick) { this.onClick = onClick; return this; }

        /** Translation key of the name above the NPC. Defaults to {@code <game>.npc.<id>}. */
        public Builder name(String nameKey) { this.nameKey = nameKey; return this; }

        /** Entity to spawn. Defaults to a villager. */
        public Builder entity(EntityType type) { this.entityType = type; return this; }

        /** Arena states the NPC is present in. Defaults to LIVE. */
        public Builder activeIn(ArenaState first, ArenaState... rest) {
            this.activeStates = EnumSet.of(first, rest);
            return this;
        }

        /** Where to stand on maps without points for this NPC. */
        public Builder defaultSpots(Function<Arena, List<Location>> spots) { this.defaultSpots = spots; return this; }

        public NpcDefinition build() {
            String key = nameKey != null ? nameKey : game.value() + ".npc." + id;
            return new NpcDefinition(game, id, menuId, onClick, key, entityType, activeStates, defaultSpots);
        }
    }
}
