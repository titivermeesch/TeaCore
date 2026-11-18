package me.playbosswar.tea.core.impl.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.combat.KillCredit;
import org.junit.jupiter.api.Test;

class CombatTrackerImplTest {

    private static final long NOW = 100_000L;
    private final UUID victim = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    private CombatTrackerImpl tracker() {
        return new CombatTrackerImpl(null, null, () -> NOW, () -> 6_000L, () -> 8_000L);
    }

    @Test
    void mostRecentHitIsTheKillerOthersAssist() {
        CombatTrackerImpl t = tracker();
        t.putHit(victim, a, NOW - 5_000);
        t.putHit(victim, b, NOW - 1_000);
        t.putHit(victim, c, NOW - 3_000);
        KillCredit credit = t.resolve(victim, null, NOW);
        assertEquals(b, credit.killer());
        assertEquals(List.of(c, a), credit.assists());
    }

    @Test
    void hitsOutsideTheWindowsDoNotCount() {
        CombatTrackerImpl t = tracker();
        t.putHit(victim, a, NOW - 7_000); // too old to kill, recent enough to assist
        t.putHit(victim, b, NOW - 9_000); // too old for anything
        KillCredit credit = t.resolve(victim, null, NOW);
        assertNull(credit.killer());
        assertEquals(List.of(a), credit.assists());
    }

    @Test
    void noHistoryMeansAnUncreditedDeath() {
        KillCredit credit = tracker().resolve(victim, null, NOW);
        assertNull(credit.killer());
        assertTrue(credit.assists().isEmpty());
    }

    @Test
    void clearForgetsTheVictimsHistory() {
        CombatTrackerImpl t = tracker();
        t.putHit(victim, a, NOW - 100);
        t.clear(victim);
        assertNull(t.resolve(victim, null, NOW).killer());
    }
}
