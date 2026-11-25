package me.playbosswar.tea.core.impl.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import me.playbosswar.tea.core.impl.settings.SettingsSchema.Kind;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.Change;
import me.playbosswar.tea.core.impl.settings.SettingsServiceImpl.FileView;
import org.junit.jupiter.api.Test;

class SettingsSchemaTest {

    private static final String KITS = """
            id: game
            file: config.yml
            categories:
              - id: match
                fields:
                  - { path: game.length, type: int, min: 60, max: 600, title: Length }
                  - { path: game.mode, type: enum, options: [RESET, SHUTDOWN], title: Mode }
              - id: kits
                fields:
                  - path: kits
                    type: map
                    title: Kits
                    fields:
                      - { path: cost, type: int, min: 0, title: Cost }
                      - { path: icon, type: material, title: Icon }
                      - path: gear
                        type: list
                        from-inventory: true
                        title: Gear
                        fields:
                          - { path: material, type: material, title: Item }
                          - { path: amount, type: int, min: 1, max: 64, title: Amount }
            """;

    private static SettingsSchema schema(String yaml) {
        return SettingsSchema.parse(SettingsSchema.map(SafeYaml.create().load(yaml)));
    }

    private static FileView view(SettingsSchema schema, Map<String, Object> values) {
        return new FileView(schema, "Game", List.of("here"), values, new LinkedHashSet<>());
    }

    private static Map<String, Object> kitValues() {
        Map<String, Object> runner = new LinkedHashMap<>();
        runner.put("cost", 0);
        runner.put("gear", new java.util.ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("material", "STICK")),
                new LinkedHashMap<>(Map.of("material", "LEATHER_BOOTS")))));
        Map<String, Object> kits = new LinkedHashMap<>();
        kits.put("runner", runner);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("game.length", 300);
        values.put("kits", kits);
        return values;
    }

    @Test
    void resolvesEntriesItemsAndNestedFields() {
        SettingsSchema s = schema(KITS);
        assertThat(s.resolve("game.length").orElseThrow().kind()).isEqualTo(Kind.VALUE);
        assertThat(s.resolve("kits.runner").orElseThrow().kind()).isEqualTo(Kind.ENTRY);
        assertThat(s.resolve("kits.runner.cost").orElseThrow().field().title()).isEqualTo("Cost");
        var item = s.resolve("kits.runner.gear[1]").orElseThrow();
        assertThat(item.kind()).isEqualTo(Kind.ITEM);
        assertThat(item.listPath()).isEqualTo("kits.runner.gear");
        var amount = s.resolve("kits.runner.gear[0].amount").orElseThrow();
        assertThat(amount.field().title()).isEqualTo("Amount");
        assertThat(amount.listPath()).isEqualTo("kits.runner.gear");
        assertThat(s.resolve("kits.bad id")).isEmpty();
        assertThat(s.resolve("game.unknown")).isEmpty();
    }

    @Test
    void validatesValues() {
        SettingsSchema s = schema(KITS);
        var length = s.resolve("game.length").orElseThrow().field();
        assertThat(SettingsSchema.validate(length, "120")).isEqualTo(120);
        assertThatThrownBy(() -> SettingsSchema.validate(length, 30)).hasMessageContaining("lower than 60");
        assertThatThrownBy(() -> SettingsSchema.validate(length, 1.5)).hasMessageContaining("whole number");
        var mode = s.resolve("game.mode").orElseThrow().field();
        assertThat(SettingsSchema.validate(mode, "shutdown")).isEqualTo("SHUTDOWN");
        assertThatThrownBy(() -> SettingsSchema.validate(mode, "x")).hasMessageContaining("one of");
        var icon = s.resolve("kits.a.icon").orElseThrow().field();
        assertThat(SettingsSchema.validate(icon, "minecraft:red wool")).isEqualTo("RED_WOOL");
    }

    @Test
    void changesInsideAListBecomeOneListWrite() {
        SettingsSchema s = schema(KITS);
        List<Change> writes = SettingsServiceImpl.toWrites(view(s, kitValues()), List.of(
                new Change("kits.runner.gear[0].amount", 2),
                new Change("kits.runner.gear[2]", Map.of("material", "BOW")),
                new Change("kits.runner.gear[1]", null)));
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).path()).isEqualTo("kits.runner.gear");
        assertThat(writes.get(0).value()).isEqualTo(List.of(
                Map.of("material", "STICK", "amount", 2), Map.of("material", "BOW")));
    }

    @Test
    void entryChangesMergeIntoTheEntryWrite() {
        SettingsSchema s = schema(KITS);
        List<Change> writes = SettingsServiceImpl.toWrites(view(s, kitValues()), List.of(
                new Change("kits.zapper", Map.of("cost", 100)),
                new Change("kits.zapper.icon", "blaze_rod"),
                new Change("game.length", 120)));
        assertThat(writes).extracting(Change::path).containsExactly("kits.zapper", "game.length");
        assertThat(writes.get(0).value()).isEqualTo(Map.of("cost", 100, "icon", "BLAZE_ROD"));
    }

    @Test
    void rejectsUnknownAndInvalidChanges() {
        SettingsSchema s = schema(KITS);
        FileView v = view(s, kitValues());
        assertThatThrownBy(() -> SettingsServiceImpl.toWrites(v, List.of(new Change("nope", 1))))
                .hasMessageContaining("Unknown setting");
        assertThatThrownBy(() -> SettingsServiceImpl.toWrites(v, List.of(new Change("kits.runner.cost", -5))))
                .hasMessageContaining("lower than 0");
    }

    @Test
    void bundledSchemasParse() throws Exception {
        for (String name : List.of("settings/teacore.yml", "settings/quests.yml")) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
                SettingsSchema s = SettingsSchema.parse(SettingsSchema.map(SafeYaml.create().load(in)));
                assertThat(s.fields()).isNotEmpty();
            }
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("settings/quests.yml")) {
            SettingsSchema s = SettingsSchema.parse(SettingsSchema.map(SafeYaml.create().load(in)));
            assertThat(s.resolve("weekly.win-15.reward.coins").orElseThrow().field().title())
                    .isEqualTo("Coin reward");
        }
    }

    @Test
    void bundledSchemasPointAtKeysThatExist() throws Exception {
        for (String[] pair : List.of(new String[] {"settings/teacore.yml", "config.yml"},
                new String[] {"settings/quests.yml", "quests.yml"})) {
            SettingsSchema s;
            Object doc;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(pair[0])) {
                s = SettingsSchema.parse(SettingsSchema.map(SafeYaml.create().load(in)));
            }
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(pair[1])) {
                doc = SafeYaml.create().load(in);
            }
            for (var f : s.fields()) {
                assertThat(SettingsValues.get(doc, f.path())).as(pair[1] + ": " + f.path()).isNotNull();
            }
        }
    }
}
