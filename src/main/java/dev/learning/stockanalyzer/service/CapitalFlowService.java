package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.entity.WatchlistEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CapitalFlowService {

    private static final Logger log = LoggerFactory.getLogger(CapitalFlowService.class);
    private static final String SINA_FLOW_API =
            "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/"
                    + "MoneyFlow.ssl_qsfx_lscjfb?page=1&num=5&sort=opendate&asc=0&daima=";

    private final WatchlistService watchlistService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public CapitalFlowService(WatchlistService watchlistService, ObjectMapper objectMapper) {
        this.watchlistService = watchlistService;
        this.objectMapper = objectMapper;
    }

    public CapitalFlowResponse getWatchlistCapitalFlows() {
        List<CapitalFlowItem> items = watchlistService.getAllEntities().stream()
                .map(this::fetchCapitalFlow)
                .sorted(Comparator.comparingDouble((CapitalFlowItem item) ->
                        item.latest() == null ? Double.NEGATIVE_INFINITY : item.latest().netAmount()).reversed())
                .toList();
        return new CapitalFlowResponse(items);
    }

    private CapitalFlowItem fetchCapitalFlow(WatchlistEntity entity) {
        StockInfo stock = new StockInfo(
                entity.getCode(), entity.getMarket(), entity.getName(), entity.getIndustry());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SINA_FLOW_API + entity.getFullCode()))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new CapitalFlowItem(stock, null, List.of(), false,
                        "资金流接口状态异常: " + response.statusCode());
            }

            List<CapitalFlowDay> recent = parseFlowResponse(response.body());
            return new CapitalFlowItem(
                    stock,
                    recent.isEmpty() ? null : recent.get(0),
                    recent,
                    !recent.isEmpty(),
                    recent.isEmpty() ? "暂无资金流数据" : null);
        } catch (Exception e) {
            log.warn("获取资金流数据失败: {}", entity.getFullCode(), e);
            return new CapitalFlowItem(stock, null, List.of(), false, "资金流数据暂不可用");
        }
    }

    List<CapitalFlowDay> parseFlowResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) return List.of();

        List<CapitalFlowDay> result = new ArrayList<>();
        for (JsonNode item : root) {
            result.add(new CapitalFlowDay(
                    item.path("opendate").asText(),
                    number(item, "trade"),
                    number(item, "changeratio") * 100,
                    number(item, "netamount"),
                    number(item, "ratioamount") * 100,
                    number(item, "r0_net"),
                    number(item, "r1_net"),
                    number(item, "r2_net"),
                    number(item, "r3_net")
            ));
        }
        return List.copyOf(result);
    }

    private double number(JsonNode item, String field) {
        String value = item.path(field).asText("0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public record CapitalFlowResponse(List<CapitalFlowItem> items) {
    }

    public record CapitalFlowItem(
            StockInfo stock,
            CapitalFlowDay latest,
            List<CapitalFlowDay> recent,
            boolean available,
            String message
    ) {
    }

    public record CapitalFlowDay(
            String date,
            double closePrice,
            double changePercent,
            double netAmount,
            double netRatio,
            double superLargeNet,
            double largeNet,
            double mediumNet,
            double smallNet
    ) {
    }
}
