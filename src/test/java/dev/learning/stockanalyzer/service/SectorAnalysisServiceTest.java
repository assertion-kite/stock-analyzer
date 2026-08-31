package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.config.FundamentalsProperties;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorDetailResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorSearchResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SectorAnalysisServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SectorAnalysisService service = new SectorAnalysisService(
            new FundamentalsProperties(), objectMapper, mock(HttpClient.class), mock(SectorRankingLogService.class));

    @Test
    void shouldParseIntradayStrengthResponse() throws Exception {
        String json = """
                {
                  "available":true,
                  "sector":{"id":"new_jxhy","name":"机械行业","companyCount":211,"score":75.9},
                  "selectedCode":"sz002747",
                  "stocks":[{
                    "fullCode":"sz002747",
                    "code":"002747",
                    "name":"埃斯顿",
                    "selected":true,
                    "return1m":0.03,
                    "return3m":0.28,
                    "return5m":0.25,
                    "amplitude5m":0.44,
                    "volumeRatio":1.83,
                    "volumeExpanded":true,
                    "score":69.1,
                    "rank":2,
                    "signals":["放量上攻"],
                    "points":[{"time":"14:59","price":36.12,"volume":1000}]
                  }],
                  "warnings":[]
                }
                """;

        SectorDetailResponse response = objectMapper.readValue(json, SectorDetailResponse.class);

        assertThat(response.available()).isTrue();
        assertThat(response.sector().name()).isEqualTo("机械行业");
        assertThat(response.stocks()).hasSize(1);
        assertThat(response.stocks().get(0).selected()).isTrue();
        assertThat(response.stocks().get(0).volumeExpanded()).isTrue();
        assertThat(response.stocks().get(0).points()).hasSize(1);
    }

    @Test
    void shouldParseConceptSectorSearchResponse() throws Exception {
        String json = """
                {
                  "results":[{
                    "id":"gn_zjqrgn",
                    "name":"机器人概念",
                    "type":"概念",
                    "companyCount":45,
                    "changePercent":0.10,
                    "score":66.3,
                    "status":"活跃"
                  }],
                  "warnings":[],
                  "fetchedAt":"2026-08-11T18:20:00"
                }
                """;

        SectorSearchResponse response = objectMapper.readValue(json, SectorSearchResponse.class);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).id()).isEqualTo("gn_zjqrgn");
        assertThat(response.results().get(0).type()).isEqualTo("概念");
    }
}
