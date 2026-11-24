package me.playbosswar.tea.core.impl.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import me.playbosswar.tea.core.api.coordination.ArenaSnapshot.RosterEntry;
import org.junit.jupiter.api.Test;

class RosterEncodingTest {

    @Test
    void rosterRoundTrips() {
        List<RosterEntry> roster = List.of(
                new RosterEntry(UUID.randomUUID(), "Alice", "red", false, 3, 1, 2),
                new RosterEntry(UUID.randomUUID(), "Bob", null, true));
        assertThat(RedisBackend.decodeRoster(RedisBackend.encodeRoster(roster))).isEqualTo(roster);
    }

    @Test
    void oldFourFieldEntriesStillDecode() {
        UUID id = UUID.randomUUID();
        assertThat(RedisBackend.decodeRoster(id + ";Bob;blue;p"))
                .containsExactly(new RosterEntry(id, "Bob", "blue", false, 0, 0, 0));
    }

    @Test
    void sidebarRoundTrips() {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        assertThat(RedisBackend.decodeSidebar(h)).isNull();
        h.put("sb_viewer", "Alice");
        h.put("sb_title", "Fireball");
        h.put("sb_lines", "Red: 3\nBlue: 1");
        assertThat(RedisBackend.decodeSidebar(h).lines()).containsExactly("Red: 3", "Blue: 1");
    }

    @Test
    void emptyAndMalformedRostersDecodeSafely() {
        assertThat(RedisBackend.decodeRoster("")).isEmpty();
        assertThat(RedisBackend.decodeRoster(null)).isEmpty();
        assertThat(RedisBackend.decodeRoster("not-a-uuid;x;;p|garbage")).isEmpty();
    }
}
