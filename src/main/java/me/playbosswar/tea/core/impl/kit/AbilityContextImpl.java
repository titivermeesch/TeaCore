package me.playbosswar.tea.core.impl.kit;

import java.time.Duration;
import java.util.Map;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.cooldown.CooldownDisplay;
import me.playbosswar.tea.core.api.cooldown.CooldownService;
import me.playbosswar.tea.core.api.kit.AbilityContext;
import org.bukkit.entity.Player;

public final class AbilityContextImpl implements AbilityContext {

    private final Player player;
    private final Arena arena;
    private final String cooldownId;
    private final CooldownDisplay display;
    private final Map<String, Object> config;
    private final CooldownService cooldowns;

    public AbilityContextImpl(
            Player player,
            Arena arena,
            String cooldownId,
            CooldownDisplay display,
            Map<String, Object> config,
            CooldownService cooldowns) {
        this.player = player;
        this.arena = arena;
        this.cooldownId = cooldownId;
        this.display = display;
        this.config = config;
        this.cooldowns = cooldowns;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public Arena arena() {
        return arena;
    }

    @Override
    public Map<String, Object> config() {
        return config;
    }

    @Override
    public void startCooldown(Duration duration) {
        cooldowns.start(player, cooldownId, duration, display);
    }

    @Override
    public boolean isOnCooldown() {
        return cooldowns.isActive(player, cooldownId);
    }
}
