package me.playbosswar.tea.core.api.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import me.playbosswar.tea.core.api.game.GameId;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MultiPointExtensionTest {

    private final MultiPointExtension chests = MultiPointExtension.builder(MultiPointExtension.key("fireball:chests"))
            .subcommand("chest").label("chest").build();

    @Test
    void gameComesFromTheIdPrefix() {
        assertThat(chests.game()).contains(GameId.of("fireball"));
        assertThat(chests.appliesTo(GameId.of("fireball"))).isTrue();
        assertThat(chests.appliesTo(GameId.of("ascend"))).isFalse();
    }

    @Test
    void idWithoutPrefixAppliesToEveryGame() {
        MultiPointExtension shared = MultiPointExtension.builder(MultiPointExtension.key("decor"))
                .subcommand("decor").build();
        assertThat(shared.game()).isEmpty();
        assertThat(shared.appliesTo(GameId.of("ascend"))).isTrue();
    }

    @Test
    void placeAndRemoveKeepIdsInOrder() {
        List<SpawnPoint> v = chests.placePoint(null, SpawnPoint.of("x", 1, 2, 3));
        v = chests.placePoint(v, SpawnPoint.of("x", 4, 5, 6));
        v = chests.placePoint(v, SpawnPoint.of("x", 7, 8, 9));
        assertThat(v).extracting(SpawnPoint::id).containsExactly("chest-0", "chest-1", "chest-2");

        v = chests.removePoint(v, 1);
        assertThat(v).extracting(SpawnPoint::id).containsExactly("chest-0", "chest-1");
        assertThat(v).extracting(SpawnPoint::x).containsExactly(1.0, 7.0);
    }

    @Test
    void removingTheLastPointClearsTheValue() {
        List<SpawnPoint> v = chests.placePoint(null, SpawnPoint.of("x", 1, 2, 3));
        assertThat(chests.removePoint(v, 0)).isNull();
        assertThat(chests.removePoint(v, 5)).isSameAs(v);
    }

    @Test
    void yamlRoundTripKeepsTheOldFormat() {
        List<SpawnPoint> v = chests.placePoint(null, new SpawnPoint("x", 1.5, 2, 3.5, 90f, 0f));
        YamlConfiguration yaml = new YamlConfiguration();
        chests.toYaml(v, yaml.createSection("ext"));
        assertThat(yaml.getMapList("ext.points")).hasSize(1);
        List<SpawnPoint> back = chests.fromYaml(yaml.getConfigurationSection("ext"));
        assertThat(back).containsExactly(new SpawnPoint("chest-0", 1.5, 2, 3.5, 90f, 0f));
    }

    @Test
    void relativeAndAbsoluteAreInverse() {
        List<SpawnPoint> v = chests.placePoint(null, SpawnPoint.of("x", 10, 20, 30));
        assertThat(chests.toAbsolute(chests.toRelative(v, 5, 6, 7), 5, 6, 7)).isEqualTo(v);
    }

    @Test
    void spawnPointExtensionExposesItsSinglePoint() {
        SpawnPointExtension ext = SpawnPointExtension.builder(MapExtensionKey.of("ascend:mob", SpawnPoint.class))
                .subcommand("mob").build();
        SpawnPoint placed = ext.placePoint(null, SpawnPoint.of("whatever", 1, 2, 3));
        assertThat(placed.id()).isEqualTo("ascend:mob");
        assertThat(ext.points(placed)).containsExactly(placed);
        assertThat(ext.removePoint(placed, 0)).isNull();
        assertThat(ext.game()).contains(GameId.of("ascend"));
    }
}
