package me.playbosswar.tea.core.impl.kit;

import java.time.Duration;
import java.util.Optional;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.arena.ArenaManager;
import me.playbosswar.tea.core.api.arena.ArenaState;
import me.playbosswar.tea.core.api.cooldown.CooldownService;
import me.playbosswar.tea.core.api.kit.Ability;
import me.playbosswar.tea.core.api.kit.AbilityBinding;
import me.playbosswar.tea.core.api.kit.AbilityContext;
import me.playbosswar.tea.core.api.kit.AbilityTrigger;
import me.playbosswar.tea.core.api.kit.Kit;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Dispatches Bukkit events to registered abilities whose kit the player has
 * selected.
 * Abilities are only triggered in arenas in {@link ArenaState#LIVE} state.
 */
public final class AbilityTriggerListener implements Listener {

    private final KitRegistryImpl kitRegistry;
    private final ArenaManager arenaManager;
    private final CooldownService cooldowns;

    public AbilityTriggerListener(KitRegistryImpl kitRegistry, ArenaManager arenaManager, CooldownService cooldowns) {
        this.kitRegistry = kitRegistry;
        this.arenaManager = arenaManager;
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        AbilityTrigger t = switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> AbilityTrigger.RIGHT_CLICK_ITEM;
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> AbilityTrigger.LEFT_CLICK_ITEM;
            default -> null;
        };
        if (t == null)
            return;
        fire(event.getPlayer(), t, event);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        fire(event.getPlayer(),
                event.isSneaking() ? AbilityTrigger.SHIFT_PRESS : AbilityTrigger.SHIFT_RELEASE, event);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            fire(p, AbilityTrigger.DAMAGE_TAKEN, event);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            fire(killer, AbilityTrigger.KILL, event);
        }
    }

    private void fire(Player player, AbilityTrigger trigger, Event source) {
        Optional<Arena> optArena = arenaManager.arenaOf(player.getUniqueId());
        if (optArena.isEmpty())
            return;
        Arena arena = optArena.get();
        if (arena.state() != ArenaState.LIVE)
            return;

        Optional<Kit> optKit = kitRegistry.selectedKit(player, arena.gameId());
        if (optKit.isEmpty())
            return;

        for (AbilityBinding binding : optKit.get().abilities()) {
            if (binding.trigger() != trigger)
                continue;
            Supplier<Ability> factory = kitRegistry.abilityFactory(arena.gameId(), binding.abilityId());
            if (factory == null)
                continue;
            String cooldownId = optKit.get().kitId() + "#" + binding.abilityId();
            if (cooldowns.isActive(player, cooldownId))
                continue;
            AbilityContext ctx = new AbilityContextImpl(
                    player, arena, cooldownId, binding.display(), binding.config(), cooldowns);
            try {
                factory.get().onTrigger(ctx);
                if (binding.cooldownTicks() > 0) {
                    ctx.startCooldown(Duration.ofMillis(binding.cooldownTicks() * 50L));
                }
            } catch (Throwable t) {
                player.getServer().getLogger().warning(
                        "Ability " + binding.abilityId() + " threw: " + t.getMessage());
            }
        }
    }
}
