package me.playbosswar.tea.core.impl.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
        db = new SQLiteDataSource();
        db.setUrl("jdbc:sqlite:" + tmp.resolve("tea.db"));
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migrations/core/V013__shared_files.sql")) {
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

    private SharedFiles server(String name) throws Exception {
        Path folder = tmp.resolve(name);
        Files.createDirectories(folder);
        return new SharedFiles(db, folder, name, logger);
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
        lobby.pullAtStartup();

        SharedFiles arena = server("arena");
        assertThat(arena.pullAtStartup()).containsExactlyInAnyOrder("maps/fireball.yml", "maps/schems/big.schem");
        assertThat(read("arena", "maps/fireball.yml")).isEqualTo("maps: {}\n");
        assertThat(Files.readAllBytes(tmp.resolve("arena/maps/schems/big.schem"))).isEqualTo(big);
    }

    @Test
    void changesAndDeletesReachOtherServers() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "v1\n");
        write("a", "waiting-rooms/waiting-rooms.yml", "rooms: {}\n");
        a.pullAtStartup();
        SharedFiles b = server("b");
        b.pullAtStartup();

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
        a.pullAtStartup();
        SharedFiles b = server("b");
        b.pullAtStartup();

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
    void bakedInFilesAreSetAsideOnlyAtStartup() throws Exception {
        SharedFiles a = server("a");
        write("a", "maps/fireball.yml", "shared\n");
        a.pullAtStartup();

        write("b", "maps/old.yml", "from the image\n");
        SharedFiles b = server("b");
        b.pullAtStartup();
        assertThat(tmp.resolve("b/maps/old.yml")).doesNotExist();
        try (var walk = Files.walk(tmp.resolve("b/replaced-by-database"))) {
            assertThat(walk.anyMatch(p -> p.endsWith("old.yml"))).isTrue();
        }

        write("b", "maps/new.yml", "saved here\n");
        b.pull();
        assertThat(tmp.resolve("b/maps/new.yml")).exists();
        assertThat(b.push()).isTrue();
        a.pull();
        assertThat(read("a", "maps/new.yml")).isEqualTo("saved here\n");
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
