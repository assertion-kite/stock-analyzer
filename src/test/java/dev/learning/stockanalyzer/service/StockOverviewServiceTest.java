package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockOverviewServiceTest {

    private final StockSearchService searchService = mock(StockSearchService.class);
    private final StockDataService dataService = mock(StockDataService.class);
    private final StockOverviewService service = new StockOverviewService(searchService, dataService);

    @Test
    void shouldUseQuoteNameWhenStockIsNotInCatalog() {
        StockQuote quote = quote("sh688001", "华兴源创");
        when(searchService.findByCode("sh688001")).thenReturn(Optional.empty());
        when(dataService.getQuote("sh688001")).thenReturn(quote);

        StockOverviewService.StockOverview result = service.getOverview("688001");

        assertThat(result.stock()).isEqualTo(new StockInfo("688001", "sh", "华兴源创", "其他"));
        assertThat(result.quote()).isSameAs(quote);
    }

    private StockQuote quote(String code, String name) {
        return new StockQuote(code, name, 20, 19, 19.5, 20.5, 19.2,
                1_000_000, 20_000_000, 5.26, "20260811150000");
    }
}
