package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CapitalFlowServiceTest {

    private final CapitalFlowService service = new CapitalFlowService(
            mock(WatchlistService.class), new ObjectMapper());

    @Test
    void shouldParseSinaCapitalFlowResponse() throws Exception {
        String response = """
                [{
                  "opendate":"2026-08-10",
                  "trade":"36.12",
                  "changeratio":"0.043",
                  "netamount":"12500000",
                  "ratioamount":"0.0825",
                  "r0_net":"3500000",
                  "r1_net":"4000000",
                  "r2_net":"3000000",
                  "r3_net":"2000000"
                }]
                """;

        List<CapitalFlowService.CapitalFlowDay> result = service.parseFlowResponse(response);

        assertThat(result).hasSize(1);
        CapitalFlowService.CapitalFlowDay day = result.get(0);
        assertThat(day.date()).isEqualTo("2026-08-10");
        assertThat(day.closePrice()).isEqualTo(36.12);
        assertThat(day.changePercent()).isEqualTo(4.3);
        assertThat(day.netAmount()).isEqualTo(12_500_000);
        assertThat(day.netRatio()).isEqualTo(8.25);
        assertThat(day.superLargeNet()).isEqualTo(3_500_000);
        assertThat(day.largeNet()).isEqualTo(4_000_000);
        assertThat(day.mediumNet()).isEqualTo(3_000_000);
        assertThat(day.smallNet()).isEqualTo(2_000_000);
    }

    @Test
    void shouldReturnEmptyListForUnexpectedPayload() throws Exception {
        assertThat(service.parseFlowResponse("{}")) .isEmpty();
    }
}
