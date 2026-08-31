package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.ai.PeerRecommendation;
import dev.learning.stockanalyzer.ai.PeerRecommendationAgent;
import dev.learning.stockanalyzer.ai.StockDetailAgent;
import dev.learning.stockanalyzer.ai.StockDetailResult;
import dev.learning.stockanalyzer.ai.StockScoreResult;
import dev.learning.stockanalyzer.ai.StockScoringAgent;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockScoringService {

    private static final Logger log = LoggerFactory.getLogger(StockScoringService.class);

    private final StockDataService stockDataService;
    private final StockSearchService stockSearchService;
    private final StockScoringAgent scoringAgent;
    private final PeerRecommendationAgent peerAgent;
    private final StockDetailAgent detailAgent;

    public StockScoringService(StockDataService stockDataService,
                               StockSearchService stockSearchService,
                               StockScoringAgent scoringAgent,
                               PeerRecommendationAgent peerAgent,
                               StockDetailAgent detailAgent) {
        this.stockDataService = stockDataService;
        this.stockSearchService = stockSearchService;
        this.scoringAgent = scoringAgent;
        this.peerAgent = peerAgent;
        this.detailAgent = detailAgent;
    }

    public ScoreResponse scoreByKeyword(String keyword) {
        List<StockInfo> results = stockSearchService.search(keyword);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("未找到匹配股票: " + keyword);
        }
        StockInfo stock = results.get(0);
        StockQuote quote = stockDataService.getQuote(stock.fullCode());
        String context = buildScoringContext(stock, quote);

        log.debug("AI评分请求: {}", stock.name());
        StockScoreResult score = scoringAgent.score(context);

        return new ScoreResponse(stock, quote, score);
    }

    public PeerRecommendation recommendPeers(String fullCode) {
        StockInfo target = stockSearchService.findByCode(fullCode).orElse(null);
        if (target == null) {
            // 从行情获取基本信息
            StockQuote quote = stockDataService.getQuote(fullCode);
            String code = fullCode.replaceAll("^(sh|sz)", "");
            String market = fullCode.startsWith("sh") ? "sh" : "sz";
            target = new StockInfo(code, market, quote.name(), "其他");
        }
        StockQuote targetQuote = stockDataService.getQuote(fullCode);

        String context = buildPeerContext(target, targetQuote);
        log.debug("AI同行推荐请求: {}", target.name());
        return peerAgent.recommend(context);
    }

    private String buildScoringContext(StockInfo stock, StockQuote quote) {
        return """
                目标股票信息：
                - 股票：%s（%s）
                - 所属行业：%s
                - 当前价格：%.2f元
                - 昨收：%.2f元
                - 今开：%.2f元
                - 最高：%.2f元
                - 最低：%.2f元
                - 成交量：%d手
                - 涨跌幅：%+.2f%%
                - 数据时间：%s

                请对该股票进行综合评分。
                """.formatted(stock.name(), stock.fullCode(), stock.industry(),
                quote.currentPrice(), quote.yesterdayClose(), quote.openPrice(),
                quote.highPrice(), quote.lowPrice(), quote.volume() / 100,
                quote.changePercent(), quote.dateTime());
    }

    private String buildPeerContext(StockInfo target, StockQuote targetQuote) {
        return """
                目标股票：%s（%s）
                当前价格：%.2f元，涨跌幅：%+.2f%%
                成交量：%d手，成交额：%.2f万元

                请根据你对A股市场的了解：
                1. 首先判断该公司所属的细分行业（不要使用"其他"，要给出具体行业如"白酒"、"半导体"、"新能源汽车"等）
                2. 然后从该行业中推荐3-5只比目标股票更具投资价值的龙头股票
                3. 综合考虑行业地位、市值规模、业绩增速、估值水平和竞争壁垒
                4. 给出该行业的整体分析概述
                """.formatted(target.name(), target.fullCode(),
                targetQuote.currentPrice(), targetQuote.changePercent(),
                targetQuote.volume() / 100, targetQuote.turnover());
    }

    public record ScoreResponse(StockInfo stock, StockQuote quote, StockScoreResult score) {
    }

    public DetailResponse getStockDetail(String fullCode) {
        StockInfo info = stockSearchService.findByCode(fullCode).orElse(null);
        if (info == null) {
            // 尝试从行情接口获取基本信息
            StockQuote quote = stockDataService.getQuote(fullCode);
            String code = fullCode.replaceAll("^(sh|sz)", "");
            String market = fullCode.startsWith("sh") ? "sh" : "sz";
            info = new StockInfo(code, market, quote.name(), "其他");
            String context = buildDetailContext(info, quote);
            log.debug("AI详情分析请求: {}", info.name());
            StockDetailResult detail = detailAgent.analyze(context);
            return new DetailResponse(info, quote, detail);
        }
        StockQuote quote = stockDataService.getQuote(fullCode);
        String context = buildDetailContext(info, quote);

        log.debug("AI详情分析请求: {}", info.name());
        StockDetailResult detail = detailAgent.analyze(context);
        return new DetailResponse(info, quote, detail);
    }

    private String buildDetailContext(StockInfo stock, StockQuote quote) {
        return """
                分析目标股票：%s（%s）
                所属行业：%s
                实时行情：
                - 当前价格：%.2f元
                - 昨收：%.2f元
                - 涨跌幅：%+.2f%%
                - 成交量：%d手
                - 今日最高：%.2f元
                - 今日最低：%.2f元
                - 数据时间：%s

                请对该公司进行全面的基本面分析。
                """.formatted(stock.name(), stock.fullCode(), stock.industry(),
                quote.currentPrice(), quote.yesterdayClose(),
                quote.changePercent(), quote.volume() / 100,
                quote.highPrice(), quote.lowPrice(), quote.dateTime());
    }

    public record DetailResponse(StockInfo stock, StockQuote quote, StockDetailResult detail) {
    }
}
