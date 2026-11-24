package me.playbosswar.tea.core.impl.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.history.MatchHistoryService.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchHistorySearchTest {

    @TempDir
    Path dir;
    private HikariDataSource ds;
    private MatchHistoryServiceImpl history;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dir.resolve("t.db"));
        ds = new HikariDataSource(hc);
        for (String file : new String[]{"V008__match_history.sql", "V009__dashboard.sql"}) {
            try (InputStream in = getClass().getResourceAsStream("/db/migrations/core/" + file);
                 Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                for (String stmt : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";\\s*\\n")) {
                    if (!stmt.isBlank()) {
                        s.executeUpdate(stmt);
                    }
                }
            }
        }
        history = new MatchHistoryServiceImpl(null, null, ds, Logger.getLogger("test"));
        Instant now = Instant.now();
        insert("fireball", "coliseum", now.minus(1, ChronoUnit.DAYS), "red", alice, "Alice", bob, "Bob");
        insert("fireball", "docks", now.minus(10, ChronoUnit.DAYS), null, alice, "Alice", null, null);
        insert("ascend", "coliseum", now.minus(100, ChronoUnit.DAYS), null, bob, "Bob", null, null);
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    private void insert(String game, String map, Instant ended, String winnerTeam,
                        UUID p1, String n1, UUID p2, String n2) throws Exception {
        String id = UUID.randomUUID().toString();
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_match_history (match_id, game_id, map_id, "
                    + "ended_at, duration_seconds, winner_team) VALUES (?, ?, ?, ?, 60, ?)")) {
                ps.setString(1, id);
                ps.setString(2, game);
                ps.setString(3, map);
                ps.setTimestamp(4, Timestamp.from(ended));
                ps.setString(5, winnerTeam);
                ps.executeUpdate();
            }
            for (Object[] p : new Object[][]{{p1, n1}, {p2, n2}}) {
                if (p[0] == null) {
                    continue;
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO tea_match_players (match_id, uuid, team, "
                        + "kills, deaths, assists, result, name) VALUES (?, ?, 'red', 2, 1, 0, 'win', ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, p[0].toString());
                    ps.setString(3, (String) p[1]);
                    ps.executeUpdate();
                }
            }
        }
    }

    private static Query q(String game, String map, String player, Instant from, Instant to) {
        return new Query(game, map, player, from, to, 50, 0);
    }

    @Test
    void filtersCombine() {
        assertThat(history.count(q(null, null, null, null, null))).isEqualTo(3);
        assertThat(history.count(q("fireball", null, null, null, null))).isEqualTo(2);
        assertThat(history.count(q(null, "coliseum", null, null, null))).isEqualTo(2);
        assertThat(history.count(q(null, null, "alice", null, null))).isEqualTo(2);
        assertThat(history.count(q(null, null, bob.toString(), null, null))).isEqualTo(2);
        assertThat(history.count(q("fireball", null, "bob", null, null))).isEqualTo(1);
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        assertThat(history.count(q(null, null, null, weekAgo, null))).isEqualTo(1);
        assertThat(history.count(q(null, null, null, null, weekAgo))).isEqualTo(2);
    }

    @Test
    void searchIsNewestFirstAndPaged() {
        var page1 = history.search(new Query(null, null, null, null, null, 2, 0));
        var page2 = history.search(new Query(null, null, null, null, null, 2, 2));
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).endedAt()).isAfter(page1.get(1).endedAt());
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).gameId()).isEqualTo("ascend");
    }

    @Test
    void participantsCarryNames() {
        var m = history.search(q("fireball", "coliseum", null, null, null)).get(0);
        assertThat(history.participants(m.matchId())).extracting(p -> p.name()).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void pruneRemovesOldMatchesAndTheirPlayers() throws Exception {
        assertThat(history.pruneBefore(Instant.now().minus(90, ChronoUnit.DAYS))).isEqualTo(1);
        assertThat(history.count(q(null, null, null, null, null))).isEqualTo(2);
        assertThat(history.recentFor(bob, 10)).hasSize(1);
    }
}
