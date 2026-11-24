package me.playbosswar.tea.core.impl.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvCellTest {

    @Test
    void quotesAndNeutralizesFormulas() {
        assertThat(DashboardHistoryApi.cell(null)).isEmpty();
        assertThat(DashboardHistoryApi.cell("red")).isEqualTo("red");
        assertThat(DashboardHistoryApi.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(DashboardHistoryApi.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(DashboardHistoryApi.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    }
}
