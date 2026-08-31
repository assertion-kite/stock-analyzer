package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.ai.StockInsightAgent;
import dev.learning.stockanalyzer.ai.StockInsightResult;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockFundamentalsSnapshot;
import dev.learning.stockanalyzer.data.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockInsightService {

    private static final Logger log = LoggerFactory.getLogger(StockInsightService.class);
    private static final Map<String, String> DEFAULT_QUESTIONS = Map.of(
            "trend", "结合今天的价格区间和涨跌表现，这只股票当前走势透露了什么信号？",
            "fundamental", "结合最新业绩、估值、机构研报和同行比较，这家公司的基本面处于什么位置？",
            "risk", "当前研究这只股票时，最容易忽略哪些风险和数据盲区？",
            "review", "请给出一份简洁的综合研判，并列出下一步需要核实的数据。"
    );

    private final StockOverviewService stockOverviewService;
    private final StockInsightAgent insightAgent;
    private final ObjectMapper objectMapper;
    private final StockFundamentalsService fundamentalsService;

    public StockInsightService(StockOverviewService stockOverviewService,
                               StockInsightAgent insightAgent,
                               ObjectMapper objectMapper,
                               StockFundamentalsService fundamentalsService) {
        this.stockOverviewService = stockOverviewService;
        this.insightAgent = insightAgent;
        this.objectMapper = objectMapper;
        this.fundamentalsService = fundamentalsService;
    }

    public InsightResponse analyze(String code, String focus, String question) {
        StockOverviewService.StockOverview overview = stockOverviewService.getOverview(code);
        String normalizedFocus = focus == null || focus.isBlank() ? "review" : focus.trim();
        String actualQuestion = question == null || question.isBlank()
                ? DEFAULT_QUESTIONS.getOrDefault(normalizedFocus, DEFAULT_QUESTIONS.get("review"))
                : question.trim();
        if (actualQuestion.length() > 500) {
            throw new IllegalArgumentException("问题不能超过500个字符");
        }

        StockFundamentalsSnapshot fundamentals = fundamentalsService.get(code, false);
        String context = buildContext(
                overview.stock(), overview.quote(), fundamentals, normalizedFocus, actualQuestion);
        log.debug("AI股票研判请求: {} focus={}", overview.stock().name(), normalizedFocus);
        String rawResult = insightAgent.analyze(context);
        StockInsightResult result = parseResult(rawResult);
        return new InsightResponse(overview.stock(), overview.quote(), actualQuestion, result);
    }

    StockInsightResult parseResult(String rawResult) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawResult));
            return new StockInsightResult(
                    text(root, "headline", "AI股票研判"),
                    stringList(root.get("answer")),
                    insightPoints(root.get("keyPoints")),
                    stringList(root.get("risks")),
                    stringList(root.get("watchItems")),
                    stringList(root.get("followUpQuestions")),
                    text(root, "disclaimer", "以上为AI辅助分析，不构成投资建议。")
            );
        } catch (JsonProcessingException e) {
            log.warn("AI研判结果JSON解析失败: {}", rawResult, e);
            throw new IllegalStateException("AI返回格式不稳定，请重新提问", e);
        }
    }

    private String extractJson(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            throw new IllegalStateException("AI未返回分析内容");
        }
        int start = rawResult.indexOf('{');
        int end = rawResult.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("AI返回格式不稳定，请重新提问");
        }
        return rawResult.substring(start, end + 1);
    }

    private String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.get(field);
        return value != null && value.isValueNode() && !value.asText().isBlank()
                ? value.asText()
                : fallback;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (node.isTextual()) return node.asText().isBlank() ? List.of() : List.of(node.asText());
        if (!node.isArray()) return List.of(node.asText());

        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText();
            if (!value.isBlank()) values.add(value);
        });
        return List.copyOf(values);
    }

    private List<StockInsightResult.InsightPoint> insightPoints(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();

        List<StockInsightResult.InsightPoint> points = new ArrayList<>();
        node.forEach(item -> points.add(new StockInsightResult.InsightPoint(
                text(item, "title", "关键观察"),
                text(item, "detail", "暂无详细说明"),
                normalizeStance(text(item, "stance", "neutral"))
        )));
        return List.copyOf(points);
    }

    private String normalizeStance(String stance) {
        return switch (stance) {
            case "positive", "risk" -> stance;
            default -> "neutral";
        };
    }

    private String buildContext(StockInfo stock,
                                StockQuote quote,
                                StockFundamentalsSnapshot fundamentals,
                                String focus,
                                String question) {
        String fundamentalsContext = fundamentalsService.describeForAi(fundamentals);
        return """
                用户研究目标：%s
                用户问题：%s

                可确认的股票信息：
                - 股票：%s（%s）
                - 行业标签：%s

                可确认的实时行情：
                - 当前价：%.2f元
                - 昨收：%.2f元
                - 今开：%.2f元
                - 最高：%.2f元
                - 最低：%.2f元
                - 涨跌幅：%+.2f%%
                - 成交量：%d手
                - 成交额：%.2f万元
                - 数据时间：%s

                %s

                分析要求：
                - 明确区分实时行情、已披露财务数据、机构预测和分析判断。
                - 财务指标必须标注报告期，估值必须标注数据日期。
                - 机构盈利预测不是公司已实现业绩，不得混为一谈。
                - 对未成功获取的数据直接说明缺失，不要凭空补齐。
                """.formatted(focus, question, stock.name(), stock.fullCode(), stock.industry(),
                quote.currentPrice(), quote.yesterdayClose(), quote.openPrice(), quote.highPrice(),
                quote.lowPrice(), quote.changePercent(), quote.volume() / 100,
                quote.turnover(), quote.dateTime(), fundamentalsContext);
    }

    public record InsightResponse(
            StockInfo stock,
            StockQuote quote,
            String question,
            StockInsightResult insight
    ) {
    }
}
