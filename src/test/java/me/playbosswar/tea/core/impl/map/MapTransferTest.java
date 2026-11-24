package me.playbosswar.tea.core.impl.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.playbosswar.tea.core.api.game.GameId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapTransferTest {

    @TempDir
    Path tmp;

    private static final String FIREBALL = """
            # Fireball maps
            maps:
              coliseum:
                display-name: "Coliseum"
                schematic-file: "coliseum.schem"
                min-players: 2
              arena2:
                display-name: "Arena 2"
                schematic-file: "arena2.schem"
            """;

    private Path server(String name) throws Exception {
        Path folder = tmp.resolve(name);
        Files.createDirectories(folder.resolve("maps/schems"));
        return folder;
    }

    private static byte[] zip(String... nameThenText) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < nameThenText.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameThenText[i]));
                z.write(nameThenText[i + 1].getBytes());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void oneMapGoesFromOneServerToAnother() throws Exception {
        Path a = server("a");
        Files.writeString(a.resolve("maps/fireball.yml"), FIREBALL);
        Files.writeString(a.resolve("maps/schems/coliseum.schem"), "BUILD");
        byte[] zip = new MapTransfer(a).exportMap(GameId.of("fireball"), "coliseum");

        Path b = server("b");
        Files.writeString(b.resolve("maps/fireball.yml"), "# mine\nmaps:\n  other:\n    display-name: Other\n");
        List<String> backedUp = new ArrayList<>();
        var result = new MapTransfer(b).importZip(zip, (g, m) -> backedUp.add(m));

        assertThat(result.maps()).containsExactly("fireball/coliseum");
        assertThat(backedUp).isEmpty();
        String merged = Files.readString(b.resolve("maps/fireball.yml"));
        assertThat(merged).contains("# mine", "other:", "coliseum:", "Coliseum").doesNotContain("arena2");
        assertThat(Files.readString(b.resolve("maps/schems/coliseum.schem"))).isEqualTo("BUILD");

        new MapTransfer(b).importZip(zip, (g, m) -> backedUp.add(m));
        assertThat(backedUp).containsExactly("coliseum");
    }

    @Test
    void acceptsLooseGameFilesAndPluginFolderZips() throws Exception {
        Path b = server("b");
        new MapTransfer(b).importZip(zip(
                "overcharge.yml", "maps:\n  substation:\n    schematic-file: substation.schem\n",
                "substation.schem", "S",
                "plugins/TeaCore/waiting-rooms/waiting-rooms.yml", "rooms:\n  hall:\n    schematic: hall.schem\n",
                "TeaCore/waiting-rooms/hall.schem", "H"), null);
        assertThat(b.resolve("maps/overcharge.yml")).exists();
        assertThat(b.resolve("maps/schems/substation.schem")).exists();
        assertThat(Files.readString(b.resolve("waiting-rooms/waiting-rooms.yml"))).contains("hall:");
        assertThat(b.resolve("waiting-rooms/hall.schem")).exists();
    }

    @Test
    void exportAllSkipsOldVersions() throws Exception {
        Path a = server("a");
        Files.writeString(a.resolve("maps/fireball.yml"), FIREBALL);
        Files.createDirectories(a.resolve("maps/backups/fireball/coliseum/1"));
        Files.writeString(a.resolve("maps/backups/fireball/coliseum/1/map.yml"), "old");
        Path b = server("b");
        var result = new MapTransfer(b).importZip(new MapTransfer(a).exportAll(), null);
        assertThat(result.maps()).containsExactlyInAnyOrder("fireball/coliseum", "fireball/arena2");
        assertThat(b.resolve("maps/backups")).doesNotExist();
    }

    @Test
    void rejectsPathsOutsideTheFolders() throws Exception {
        Path b = server("b");
        assertThatThrownBy(() -> new MapTransfer(b).importZip(zip("maps/../../evil.yml", "x"), null))
                .hasMessageContaining("Refusing");
        assertThatThrownBy(() -> new MapTransfer(b).importZip(zip("readme.txt", "x"), null))
                .hasMessageContaining("no maps");
    }
}
