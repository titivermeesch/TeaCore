package me.playbosswar.tea.core.impl.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import me.playbosswar.tea.core.api.game.GameId;
import me.playbosswar.tea.core.impl.arena.Hosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SharedFilesTest {

    @TempDir
    Path tmp;
    private SQLiteDataSource db;
    private final Logger logger = Logger.getLogger("test");

    @BeforeEach
    void setUp() throws Exception {
        db = migrated("tea.db");
    }

    private SQLiteDataSource migrated(String fileName) throws Exception {
        SQLiteDataSource db = new SQLiteDataSource();
        db.setUrl("jdbc:sqlite:" + tmp.resolve(fileName));
        for (String file : List.of("V013__shared_files.sql", "V016__file_chunk_touched.sql")) {
            String sql;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/migrations/core/" + file)) {
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
                for (String stmt : sql.replaceAll("(?m)^--.*$", "").split(";")) {
                    if (!stmt.isBlank()) {
                        s.execute(stmt);
                    }
                }
            }
        }
        return db;
    }

    private void sql(String statement) throws Exception {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute(statement);
        }
    }

    private int chunkCount() throws Exception {
        try (Connection c = db.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM tea_file_chunks")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void movingToAnotherDatabaseKeepsLocalFiles() throws Exception {
        write("a", "maps/fireball.yml", "local map\n");
        server("a").pull();

        db = migrated("shared.db");
        write("b", "theme.yml", "from another server\n");
        server("b").pull();

        SharedFiles moved = server("a");
        moved.pull();
        assertThat(read("a", "maps/fireball.yml")).isEqualTo("local map\n");
        assertThat(moved.push()).isTrue();
    }

    @Test
    void contentLostAfterUploadIsRequestedAndUploadedAgain() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "v1\n");
        a.pull();
        write("a", "maps/fireball.yml", "v2\n");
        assertThat(a.push()).isTrue();
        a.takeAnnouncement();
        sql("DELETE FROM tea_file_chunks");

        write("b", "maps/fireball.yml", "v1\n");
        SharedFiles b = server("b");
        b.pull();
        assertThat(read("b", "maps/fireball.yml")).isEqualTo("v1\n");
        assertThat(b.takeAnnouncement()).isTrue();
        b.pull();
        assertThat(b.takeAnnouncement()).as("asks only once per hash").isFalse();

        assertThat(a.push()).isTrue();
        b.pull();
        assertThat(read("b", "maps/fireball.yml")).isEqualTo("v2\n");
    }

    @Test
    void pullUploadsMissingContentItHasLocally() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "v1\n");
        a.pull();
        sql("DELETE FROM tea_file_chunks");
        a.pull();
        assertThat(a.takeAnnouncement()).isTrue();
        assertThat(server("b").pull()).containsExactly("maps/fireball.yml");
    }

    @Test
    void cleanupKeepsFreshUnreferencedChunks() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "v1\n");
        a.pull();
        write("a", "maps/fireball.yml", "v2\n");
        a.push();
        assertThat(chunkCount()).isEqualTo(2);

        sql("UPDATE tea_file_chunks SET touched_at = 0");
        write("a", "maps/fireball.yml", "v3\n");
        a.push();
        assertThat(chunkCount()).isEqualTo(1);
    }

    private SharedFiles server(String name) throws Exception {
        Path folder = tmp.resolve(name);
        Files.createDirectories(folder);
        return new SharedFiles(db, folder, List.of("maps", "waiting-rooms", "theme.yml"), name, logger);
    }

    private void write(String server, String path, String text) throws Exception {
        write(server, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private void write(String server, String path, byte[] data) throws Exception {
        Path p = tmp.resolve(server).resolve(path);
        Files.createDirectories(p.getParent());
        Files.write(p, data);
    }

    private String read(String server, String path) throws Exception {
        return Files.readString(tmp.resolve(server).resolve(path));
    }

    @Test
    void firstServerUploadsAndOthersDownload() throws Exception {
        SharedFiles lobby = server("lobby");
        write("lobby", "maps/fireball.yml", "maps: {}\n");
        byte[] big = new byte[3 * 1024 * 1024 + 17];
        new Random(1).nextBytes(big);
        write("lobby", "maps/schems/big.schem", big);
        lobby.pull();

        SharedFiles arena = server("arena");
        assertThat(arena.pull()).containsExactlyInAnyOrder("maps/fireball.yml", "maps/schems/big.schem");
        assertThat(read("arena", "maps/fireball.yml")).isEqualTo("maps: {}\n");
        assertThat(Files.readAllBytes(tmp.resolve("arena/maps/schems/big.schem"))).isEqualTo(big);
    }

    @Test
    void changesAndDeletesReachOtherServers() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "v1\n");
        write("a", "waiting-rooms/waiting-rooms.yml", "rooms: {}\n");
        a.pull();
        SharedFiles b = server("b");
        b.pull();

        write("a", "maps/fireball.yml", "v2\n");
        Files.delete(tmp.resolve("a/waiting-rooms/waiting-rooms.yml"));
        assertThat(a.push()).isTrue();

        assertThat(b.pull()).containsExactlyInAnyOrder("maps/fireball.yml", "waiting-rooms/waiting-rooms.yml");
        assertThat(read("b", "maps/fireball.yml")).isEqualTo("v2\n");
        assertThat(tmp.resolve("b/waiting-rooms/waiting-rooms.yml")).doesNotExist();
        assertThat(a.push()).isFalse();
    }

    @Test
    void editsToDifferentFilesDontOverwriteEachOther() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "fb1\n");
        write("a", "maps/ascend.yml", "as1\n");
        a.pull();
        SharedFiles b = server("b");
        b.pull();

        write("a", "maps/fireball.yml", "fb2\n");
        write("b", "maps/ascend.yml", "as2\n");
        a.push();
        b.pull();
        b.push();
        a.pull();

        for (String s : List.of("a", "b")) {
            assertThat(read(s, "maps/fireball.yml")).isEqualTo("fb2\n");
            assertThat(read(s, "maps/ascend.yml")).isEqualTo("as2\n");
        }
    }

    @Test
    void filesCopiedInByHandAreUploadedAndTheDatabaseWinsForTheRest() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "shared v2\n");
        a.pull();

        write("b", "maps/fireball.yml", "stale copy from the image\n");
        write("b", "maps/overcharge.yml", "copied in by hand\n");
        SharedFiles b = server("b");
        b.pull();
        assertThat(read("b", "maps/fireball.yml")).isEqualTo("shared v2\n");
        assertThat(read("b", "maps/overcharge.yml")).isEqualTo("copied in by hand\n");
        assertThat(b.push()).isTrue();

        a.pull();
        assertThat(read("a", "maps/overcharge.yml")).isEqualTo("copied in by hand\n");
    }

    @Test
    void aFileEditedWhileTheServerWasOffIsKeptAndUploaded() throws Exception {
        write("a", "maps/fireball.yml", "v1\n");
        server("a").pull();
        SharedFiles b = server("b");
        b.pull();
        assertThat(read("b", "maps/fireball.yml")).isEqualTo("v1\n");

        write("a", "maps/fireball.yml", "edited by hand\n");
        SharedFiles restarted = server("a");
        restarted.pull();
        assertThat(read("a", "maps/fireball.yml")).isEqualTo("edited by hand\n");
        assertThat(restarted.push()).isTrue();

        b.pull();
        assertThat(read("b", "maps/fireball.yml")).isEqualTo("edited by hand\n");
    }

    @Test
    void singleFilesAreSharedAndOtherFilesAreLeftAlone() throws Exception {
        SharedFiles a = server("a");
        write("a", "theme.yml", "primary: red\n");
        write("a", "config.yml", "not shared\n");
        a.pull();
        SharedFiles b = server("b");
        b.pull();
        assertThat(read("b", "theme.yml")).isEqualTo("primary: red\n");
        assertThat(tmp.resolve("b/config.yml")).doesNotExist();
    }

    @Test
    void pickPrefersTheLongestQueue() {
        GameId fb = GameId.of("fireball");
        GameId as = GameId.of("ascend");
        assertThat(Hosting.pick(List.of(fb, as), g -> g.equals(as) ? 3 : 1, fb)).isEqualTo(as);
        assertThat(Hosting.pick(List.of(fb, as), g -> 0, fb)).isEqualTo(fb);
        assertThat(Hosting.pick(List.of(), g -> 0, null)).isNull();
    }
}
