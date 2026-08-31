package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.ai.MarketDailyPicksAgent;
import dev.learning.stockanalyzer.ai.MarketDailyResult;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDailyService {

    private static final Logger log = LoggerFactory.getLogger(MarketDailyService.class);

    private final StockDataService stockDataService;
    private final MarketDailyPicksAgent dailyPicksAgent;

    public MarketDailyService(StockDataService stockDataService,
                              MarketDailyPicksAgent dailyPicksAgent) {
        this.stockDataService = stockDataService;
        this.dailyPicksAgent = dailyPicksAgent;
    }

    public MarketDailyResult getDailyPicks() {
        List<StockQuote> indices = stockDataService.getQuotes(
                List.of("sh000001", "sz399001", "sz399006"));

        String overview = buildMarketOverview(indices);
        log.debug("AI每日精选请求");
        return dailyPicksAgent.dailyPicks(overview);
    }

    private String buildMarketOverview(List<StockQuote> indices) {
        LocalDate now = LocalDate.now();
        String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);

        StringBuilder sb = new StringBuilder();
        sb.append("今日日期：%s（%s）\n\n".formatted(now, dayOfWeek));
        sb.append("主要指数表现：\n");

        if (indices.size() >= 3) {
            sb.append("- 上证指数：%.2f点，涨跌幅%+.2f%%\n".formatted(
                    indices.get(0).currentPrice(), indices.get(0).changePercent()));
            sb.append("- 深证成指：%.2f点，涨跌幅%+.2f%%\n".formatted(
                    indices.get(1).currentPrice(), indices.get(1).changePercent()));
            sb.append("- 创业板指：%.2f点，涨跌幅%+.2f%%\n".formatted(
                    indices.get(2).currentPrice(), indices.get(2).changePercent()));
        } else if (!indices.isEmpty()) {
            for (StockQuote idx : indices) {
                sb.append("- %s：%.2f点，涨跌幅%+.2f%%\n".formatted(
                        idx.name(), idx.currentPrice(), idx.changePercent()));
            }
        }

        sb.append("\n请分析当前市场环境，并推荐3-5只适合短期关注的行业龙头股票。");
        return sb.toString();
    }
}
