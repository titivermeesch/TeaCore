package me.playbosswar.tea.core.api.combat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Who gets credit for a participant's death.
 *
 * @param victim   the player who died.
 * @param killer   the last enemy to hit the victim within the kill-credit
 *                 window, or {@code null} for an uncredited (environmental) death.
 * @param assists  other enemies who hit the victim within the assist window,
 *                 most recent first; never contains the killer; never {@code null}.
 * @param cause    damage cause of the final blow, or {@code null} if unknown.
 */
public record KillCredit(
        UUID victim,
        @Nullable UUID killer,
        List<UUID> assists,
        EntityDamageEvent.@Nullable DamageCause cause) {

    public KillCredit {
        assists = assists == null ? List.of() : List.copyOf(assists);
    }

    /** The killer, if the death was credited to a player. */
    public Optional<UUID> killerId() {
        return Optional.ofNullable(killer);
    }
}
