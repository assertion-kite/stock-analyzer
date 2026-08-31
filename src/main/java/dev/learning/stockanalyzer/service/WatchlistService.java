package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.ai.WatchlistAnalysisAgent;
import dev.learning.stockanalyzer.ai.WatchlistAnalysisResult;
import dev.learning.stockanalyzer.data.StockCodeUtils;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import dev.learning.stockanalyzer.entity.WatchlistEntity;
import dev.learning.stockanalyzer.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WatchlistService {
    private final WatchlistRepository repository;
    private final StockDataService dataService;
    private final StockSearchService searchService;
    private final WatchlistAnalysisAgent analysisAgent;

    public WatchlistService(WatchlistRepository repository, StockDataService dataService,
                            StockSearchService searchService, WatchlistAnalysisAgent analysisAgent) {
        this.repository = repository;
        this.dataService = dataService;
        this.searchService = searchService;
        this.analysisAgent = analysisAgent;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItem> list() {
        List<WatchlistEntity> entities = repository.findAllByOrderByAddedTimeDesc();
        if (entities == null || entities.isEmpty()) return List.of();
        Map<String, StockQuote> quotes = dataService.getQuotes(entities.stream().map(WatchlistEntity::getFullCode).toList())
                .stream().collect(Collectors.toMap(StockQuote::fullCode, Function.identity(), (a, b) -> a));
        return entities.stream().map(entity -> toItem(entity, quotes.get(entity.getFullCode()))).toList();
    }

    @Transactional
    public WatchlistItem add(String code) {
        String fullCode = StockCodeUtils.normalizeFullCode(code);
        if (repository.existsByFullCode(fullCode)) throw new IllegalArgumentException("股票已在自选列表");
        StockInfo stock = searchService.findByCode(fullCode).orElse(null);
        StockQuote quote = dataService.getQuote(fullCode);
        if (stock == null) stock = new StockInfo(fullCode.substring(2), fullCode.substring(0, 2), quote.name(), "其他");
        WatchlistEntity entity = new WatchlistEntity(fullCode, stock.code(), stock.market(), stock.name(), stock.industry());
        WatchlistEntity saved = repository.save(entity);
        if (saved == null) saved = entity;
        return toItem(saved, quote);
    }

    @Transactional
    public void remove(String code) {
        repository.deleteByFullCode(StockCodeUtils.normalizeFullCode(code));
    }

    @Transactional(readOnly = true)
    public boolean contains(String code) {
        return repository.existsByFullCode(StockCodeUtils.normalizeFullCode(code));
    }

    @Transactional(readOnly = true)
    public List<WatchlistEntity> getAllEntities() {
        return repository.findAllByOrderByAddedTimeDesc();
    }

    public WatchlistAnalysisResult analyze(String code) {
        String fullCode = StockCodeUtils.normalizeFullCode(code);
        StockQuote quote = dataService.getQuote(fullCode);
        StockInfo stock = searchService.findByCode(fullCode)
                .orElseGet(() -> new StockInfo(fullCode.substring(2), fullCode.substring(0, 2), quote.name(), "其他"));
        String context = "股票：%s（%s），行业：%s\n当前价：%.2f，昨收：%.2f，涨跌幅：%+.2f%%，成交量：%d手，成交额：%.2f万元，数据时间：%s"
                .formatted(stock.name(), stock.fullCode(), stock.industry(), quote.currentPrice(), quote.yesterdayClose(),
                        quote.changePercent(), quote.volume() / 100, quote.turnover(), quote.dateTime());
        return analysisAgent.analyze(context);
    }

    private WatchlistItem toItem(WatchlistEntity entity, StockQuote quote) {
        StockInfo stock = searchService.findByCode(entity.getFullCode())
                .orElse(new StockInfo(entity.getCode(), entity.getMarket(), entity.getName(), entity.getIndustry()));
        return new WatchlistItem(stock, quote, quote != null, entity.getAddedTime(), entity.getNotes(),
                quote == null ? "行情暂不可用" : null);
    }

    public record WatchlistItem(StockInfo stock, StockQuote quote, boolean quoteAvailable,
                                java.time.LocalDateTime addedTime, String notes, String message) {}
}
