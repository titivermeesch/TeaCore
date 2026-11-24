package me.playbosswar.tea.core.impl.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatAliasesTest {

    @Test
    void aliasesMapToTheGenericColumns() {
        assertThat(StatAliases.target("wins")).isEqualTo("games_won");
        assertThat(StatAliases.target("played")).isEqualTo("games_played");
        assertThat(StatAliases.target("kills")).isNull();
    }

    @Test
    void boardIdsSwapTheirSuffix() {
        assertThat(StatAliases.board("ascend_wins")).isEqualTo("ascend_games_won");
        assertThat(StatAliases.board("fireball_played")).isEqualTo("fireball_games_played");
        assertThat(StatAliases.board("ascend_kills")).isNull();
    }
}
