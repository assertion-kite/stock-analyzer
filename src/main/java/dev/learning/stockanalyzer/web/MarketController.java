package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.ai.MarketDailyResult;
import dev.learning.stockanalyzer.service.MarketDailyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDailyService marketDailyService;

    public MarketController(MarketDailyService marketDailyService) {
        this.marketDailyService = marketDailyService;
    }

    @PostMapping("/daily-picks")
    public MarketDailyResult dailyPicks() {
        return marketDailyService.getDailyPicks();
    }
}
