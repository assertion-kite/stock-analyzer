package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.StockCodeUtils;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import org.springframework.stereotype.Service;

@Service
public class StockOverviewService {

    private final StockSearchService stockSearchService;
    private final StockDataService stockDataService;

    public StockOverviewService(StockSearchService stockSearchService,
                                StockDataService stockDataService) {
        this.stockSearchService = stockSearchService;
        this.stockDataService = stockDataService;
    }

    public StockOverview getOverview(String code) {
        String fullCode = StockCodeUtils.normalizeFullCode(code);
        StockQuote quote = stockDataService.getQuote(fullCode);
        StockInfo stock = stockSearchService.findByCode(fullCode)
                .orElseGet(() -> new StockInfo(
                        fullCode.substring(2),
                        fullCode.substring(0, 2),
                        quote.name(),
                        "其他"));

        return new StockOverview(stock, quote);
    }

    public record StockOverview(StockInfo stock, StockQuote quote) {
    }
}
