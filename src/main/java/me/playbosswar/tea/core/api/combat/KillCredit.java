package me.playbosswar.tea.core.api.combat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Who gets credit for a participant's death.
 *
 * @param killer  last enemy to hit the victim within the kill-credit window, or
 *                {@code null} for an environmental death
 * @param assists other enemies who hit within the assist window, most recent first; never
 *                includes the killer
 * @param cause   damage cause of the final blow, or {@code null} if unknown
 */
public record KillCredit(
        UUID victim,
        @Nullable UUID killer,
        List<UUID> assists,
        EntityDamageEvent.@Nullable DamageCause cause) {

    public KillCredit {
        assists = assists == null ? List.of() : List.copyOf(assists);
    }

    public Optional<UUID> killerId() {
        return Optional.ofNullable(killer);
    }
}
