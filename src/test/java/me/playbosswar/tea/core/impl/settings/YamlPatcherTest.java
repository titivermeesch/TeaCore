package me.playbosswar.tea.core.impl.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlPatcherTest {

    private static final String CONFIG = """
            # Header comment
            game:
              # How long a match lasts.
              max-match-seconds: 600   # ten minutes
              respawn: true

            # Kits players pick from.
            kits:
              runner:
                cost: 0
                gear:
                  - { material: STICK, name: "<yellow>Shove Stick", knockback: 2 }
                  - LEATHER_BOOTS
              brawler:
                cost: 250
                gear:
                - STONE_SWORD

            # Daily quests.
            daily:
              win-2:
                name: "Win 2 matches"
                reward: { coins: 100 }
            tail: end
            """;

    /** Applies the change with the patcher and checks the result against the same change on the parsed map. */
    private static String patch(String text, String path, Object value) {
        String out = YamlPatcher.set(text, path, value);
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = SettingsValues.deepCopy((Map<String, Object>) YamlPatcher.load(text));
        SettingsValues.put(expected, path, value);
        assertThat(SettingsValues.same(YamlPatcher.load(out), expected))
                .as("parsed result of setting %s\n%s", path, out).isTrue();
        return out;
    }

    @Test
    void replacesAScalarAndKeepsComments() {
        String out = patch(CONFIG, "game.max-match-seconds", 900);
        assertThat(out).contains("  max-match-seconds: 900   # ten minutes");
        assertThat(out).contains("# Header comment", "# How long a match lasts.", "# Kits players pick from.",
                "# Daily quests.");
        assertThat(out.lines().count()).isEqualTo(CONFIG.lines().count());
    }

    @Test
    void addsAMissingKeyInsideASection() {
        String out = patch(CONFIG, "game.friendly-fire", false);
        assertThat(out).contains("  respawn: true\n  friendly-fire: false\n");
        assertThat(out).contains("\n# Kits players pick from.");
    }

    @Test
    void addsAMissingSection() {
        String out = patch(CONFIG, "stun-gun.range", 12);
        assertThat(out).endsWith("tail: end\nstun-gun:\n  range: 12\n");
    }

    @Test
    void rewritesAFlowMapWhenThePathRunsIntoIt() {
        String out = patch(CONFIG, "daily.win-2.reward.coins", 250);
        assertThat(out).contains("    reward:\n      coins: 250\n");
        assertThat(out).contains("name: \"Win 2 matches\"");
    }

    @Test
    void removesAnEntryAndLeavesTheNextCommentAlone() {
        String out = patch(CONFIG, "kits.brawler", null);
        assertThat(out).doesNotContain("brawler", "STONE_SWORD");
        assertThat(out).contains("LEATHER_BOOTS\n\n# Daily quests.");
    }

    @Test
    void addsAnEntryWithNestedValues() {
        Map<String, Object> kit = new LinkedHashMap<>();
        kit.put("cost", 500);
        kit.put("gear", List.of(Map.of("material", "IRON_SWORD"), Map.of("material", "BOW", "amount", 1)));
        String out = patch(CONFIG, "kits.zapper", kit);
        assertThat(out).contains("  zapper:\n    cost: 500\n    gear:\n");
    }

    @Test
    void replacesAListWrittenAtTheKeysIndent() {
        String out = patch(CONFIG, "kits.brawler.gear", List.of("IRON_SWORD", "SHIELD"));
        assertThat(out).doesNotContain("STONE_SWORD");
    }

    @Test
    void quotesStringsThatNeedIt() {
        String out = CONFIG;
        out = patch(out, "game.name", "<yellow>Shove Stick");
        out = patch(out, "game.word", "yes");
        out = patch(out, "game.colon", "a: b");
        out = patch(out, "game.empty", "");
        out = patch(out, "game.hash", "red #1");
        assertThat(out).contains("# Header comment");
    }

    @Test
    void worksOnAnEmptyDocument() {
        String out = patch("", "a.b", 1);
        assertThat(out).isEqualTo("a:\n  b: 1\n");
    }

    @Test
    void stripsCommentsOutsideQuotesOnly() {
        assertThat(YamlPatcher.stripComment("\"a # b\"  # c")).isEqualTo("\"a # b\"");
        assertThat(YamlPatcher.stripComment("red#1")).isEqualTo("red#1");
        assertThat(YamlPatcher.stripComment("600   # ten")).isEqualTo("600");
    }
}
