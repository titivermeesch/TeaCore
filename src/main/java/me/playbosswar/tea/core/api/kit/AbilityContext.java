package me.playbosswar.tea.core.api.kit;

import java.time.Duration;
import java.util.Map;
import me.playbosswar.tea.core.api.arena.Arena;
import org.bukkit.entity.Player;

/** Passed to an {@link Ability} when its trigger fires. */
public interface AbilityContext {

    Player player();

    Arena arena();

    /** The binding's {@code ability.config:} block, unchanged. */
    Map<String, Object> config();

    /**
     * Further triggers during the cooldown are ignored. Uses the
     * {@link me.playbosswar.tea.core.api.cooldown.CooldownService}, so its
     * {@link me.playbosswar.tea.core.api.cooldown.CooldownDisplay} applies.
     */
    void startCooldown(Duration duration);

    boolean isOnCooldown();
}
