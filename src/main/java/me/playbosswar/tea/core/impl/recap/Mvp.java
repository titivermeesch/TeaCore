package me.playbosswar.tea.core.impl.recap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.TeaCoreAPI;
import me.playbosswar.tea.core.api.arena.Arena;
import me.playbosswar.tea.core.api.combat.MatchStats;
import org.jetbrains.annotations.Nullable;

/** Ranks a match's players: most kills, then most damage, then fewest deaths. */
public final class Mvp {

    public record Line(UUID uuid, MatchStats stats, double damage) { }

    private Mvp() {
    }

    public static List<Line> ranking(TeaCoreAPI core, Arena arena, Collection<UUID> players) {
        List<Line> lines = new ArrayList<>();
        for (UUID uuid : players) {
            lines.add(new Line(uuid, core.combat().matchStats(arena.id(), uuid),
                    core.combat().damageDealt(arena.id(), uuid)));
        }
        lines.sort(Comparator.comparingInt((Line l) -> l.stats().kills()).reversed()
                .thenComparing(Comparator.comparingDouble(Line::damage).reversed())
                .thenComparingInt(l -> l.stats().deaths()));
        return lines;
    }

    /** Nobody is MVP in a match without a kill. */
    public static @Nullable UUID of(List<Line> ranking) {
        return ranking.isEmpty() || ranking.get(0).stats().kills() == 0 ? null : ranking.get(0).uuid();
    }
}
