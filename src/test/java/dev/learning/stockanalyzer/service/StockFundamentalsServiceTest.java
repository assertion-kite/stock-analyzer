package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.config.FundamentalsProperties;
import dev.learning.stockanalyzer.data.StockFundamentalsSnapshot;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StockFundamentalsServiceTest {

    private final StockFundamentalsService service = new StockFundamentalsService(
            new FundamentalsProperties(), new ObjectMapper(), mock(HttpClient.class));

    @Test
    void shouldParsePartialAkshareSnapshot() throws Exception {
        String json = """
                {
                  "fullCode":"sz300502",
                  "available":true,
                  "profile":{"companyName":"新易盛","industry":"通信设备","totalMarketValue":1000000000},
                  "performance":[{
                    "reportDate":"2026-06-30",
                    "revenue":5000000000,
                    "revenueYoY":42.3,
                    "netProfit":1200000000,
                    "netProfitYoY":55.8,
                    "source":"同花顺"
                  }],
                  "researchReports":[],
                  "concepts":["光模块"],
                  "sources":[{"name":"同花顺财务指标","available":true}],
                  "warnings":["估值数据暂不可用"],
                  "fetchedAt":"2026-08-11T16:30:00"
                }
                """;

        StockFundamentalsSnapshot snapshot = service.parseSnapshot(json);

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.profile().industry()).isEqualTo("通信设备");
        assertThat(snapshot.performance()).hasSize(1);
        assertThat(snapshot.performance().get(0).netProfitYoY()).isEqualTo(55.8);
        assertThat(snapshot.concepts()).containsExactly("光模块");
        assertThat(snapshot.warnings()).containsExactly("估值数据暂不可用");
        assertThat(service.describeForAi(snapshot))
                .contains("最近报告期业绩", "归母净利润=1200000000.00元", "来源=同花顺");
    }
}
