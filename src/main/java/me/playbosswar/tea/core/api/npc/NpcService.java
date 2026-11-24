package me.playbosswar.tea.core.api.npc;

import java.util.Collection;
import java.util.List;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.api.map.MapExtensionKey;
import me.playbosswar.tea.core.api.map.SpawnPoint;
import org.bukkit.entity.Entity;

/**
 * NPCs that open menus in a game's arenas (shopkeepers and the like). TeaCore
 * spawns them when an arena enters one of the NPC's active states, keeps them
 * in place and unhurt, opens the menu on right-click, and removes them when
 * the arena leaves those states.
 */
public interface NpcService {

    /**
     * Registers an NPC and its {@code /tea map <id>} point command. Call in
     * {@code onEnable}. Registering the same game and id again replaces it.
     */
    void register(NpcDefinition definition);

    /** Every NPC registered for a game, including ones defined only in config. */
    Collection<NpcDefinition> npcsFor(GameId game);

    /** Map points key for an NPC id ({@code tea:npc:<id>}). */
    MapExtensionKey<List<SpawnPoint>> pointsKey(String id);

    boolean isNpc(Entity entity);
}
