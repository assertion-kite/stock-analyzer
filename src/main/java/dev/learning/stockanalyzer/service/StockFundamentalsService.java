package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.config.FundamentalsProperties;
import dev.learning.stockanalyzer.data.StockCodeUtils;
import dev.learning.stockanalyzer.data.StockFundamentalsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockFundamentalsService {

    private static final Logger log = LoggerFactory.getLogger(StockFundamentalsService.class);

    private final FundamentalsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    @Autowired
    public StockFundamentalsService(FundamentalsProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
    }

    StockFundamentalsService(FundamentalsProperties properties,
                             ObjectMapper objectMapper,
                             HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public StockFundamentalsSnapshot get(String code, boolean refresh) {
        String fullCode = StockCodeUtils.normalizeFullCode(code);
        if (!properties.isEnabled()) {
            return StockFundamentalsSnapshot.unavailable(fullCode, "基本面数据服务已停用");
        }

        CachedSnapshot cached = cache.get(fullCode);
        if (!refresh && cached != null && !cached.isExpired(properties)) {
            return cached.snapshot();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/api/stock/"
                            + URLEncoder.encode(fullCode, StandardCharsets.UTF_8)))
                    .timeout(properties.getTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("AKShare服务状态异常: " + response.statusCode());
            }

            StockFundamentalsSnapshot snapshot = parseSnapshot(response.body());
            cache.put(fullCode, new CachedSnapshot(snapshot, Instant.now()));
            return snapshot;
        } catch (Exception e) {
            log.warn("获取基本面数据失败: {}", fullCode, e);
            if (cached != null) {
                return cached.snapshot().withWarning("数据源暂时不可用，当前展示上次成功缓存");
            }
            return StockFundamentalsSnapshot.unavailable(
                    fullCode,
                    "AKShare数据服务暂不可用，请先运行 scripts/setup-akshare.ps1 并重启项目");
        }
    }

    StockFundamentalsSnapshot parseSnapshot(String body) throws Exception {
        return objectMapper.readValue(body, StockFundamentalsSnapshot.class);
    }

    public String describeForAi(StockFundamentalsSnapshot snapshot) {
        if (snapshot == null || !snapshot.available()) {
            return "基本面数据：当前不可用。";
        }

        StringBuilder context = new StringBuilder("可确认的基本面与机构数据：\n");
        if (snapshot.profile() != null) {
            append(context, "所属行业", snapshot.profile().industry());
            append(context, "主营业务", snapshot.profile().mainBusiness());
            appendNumber(context, "总市值", snapshot.profile().totalMarketValue(), "元");
        }
        if (snapshot.performance() != null && !snapshot.performance().isEmpty()) {
            context.append("最近报告期业绩：\n");
            snapshot.performance().stream().limit(4).forEach(period -> context.append("- ")
                    .append(value(period.reportDate(), "未知日期"))
                    .append(" 营收=").append(number(period.revenue()))
                    .append("元，同比=").append(percent(period.revenueYoY()))
                    .append("；归母净利润=").append(number(period.netProfit()))
                    .append("元，同比=").append(percent(period.netProfitYoY()))
                    .append("；ROE=").append(percent(period.roe()))
                    .append("；毛利率=").append(percent(period.grossMargin()))
                    .append("；来源=").append(value(period.source(), "未标注"))
                    .append('\n'));
        }
        if (snapshot.valuation() != null) {
            StockFundamentalsSnapshot.ValuationSnapshot valuation = snapshot.valuation();
            context.append("最新估值：PE(TTM)=").append(number(valuation.peTtm()))
                    .append("，PB=").append(number(valuation.pb()))
                    .append("，PEG=").append(number(valuation.peg()))
                    .append("，PS=").append(number(valuation.ps()))
                    .append("，行业PE(TTM)=").append(number(valuation.industryPeTtm()))
                    .append("，行业估值排名=").append(value(valuation.peRank(), "暂无"))
                    .append("，数据日期=").append(value(valuation.date(), "未知"))
                    .append('\n');
        }
        if (snapshot.industryPosition() != null) {
            StockFundamentalsSnapshot.IndustryPosition position = snapshot.industryPosition();
            context.append("行业比较：估值排名=").append(value(position.valuationRank(), "暂无"))
                    .append("，成长排名=").append(value(position.growthRank(), "暂无"))
                    .append("，ROE排名=").append(value(position.roeRank(), "暂无"))
                    .append("，规模排名=").append(value(position.scaleRank(), "暂无"))
                    .append('\n');
        }
        if (snapshot.researchReports() != null && !snapshot.researchReports().isEmpty()) {
            context.append("近期机构研报：\n");
            snapshot.researchReports().stream().limit(5).forEach(report -> context.append("- ")
                    .append(value(report.date(), "未知日期")).append(' ')
                    .append(value(report.institution(), "未知机构")).append(' ')
                    .append(value(report.rating(), "未评级")).append("：《")
                    .append(value(report.title(), "未命名研报")).append("》\n"));
        }
        context.append("数据抓取时间：").append(value(snapshot.fetchedAt(), "未知")).append('\n');
        context.append("研报盈利预测属于机构观点，不等同于公司已实现业绩。\n");
        return context.toString();
    }

    private void append(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) builder.append(label).append('：').append(value).append('\n');
    }

    private void appendNumber(StringBuilder builder, String label, Double value, String unit) {
        if (value != null && Double.isFinite(value)) {
            builder.append(label).append('：').append(number(value)).append(unit).append('\n');
        }
    }

    private String number(Double value) {
        return value == null || !Double.isFinite(value) ? "暂无" : "%.2f".formatted(value);
    }

    private String percent(Double value) {
        return value == null || !Double.isFinite(value) ? "暂无" : "%+.2f%%".formatted(value);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CachedSnapshot(StockFundamentalsSnapshot snapshot, Instant loadedAt) {
        boolean isExpired(FundamentalsProperties properties) {
            return loadedAt.plus(properties.getCacheTtl()).isBefore(Instant.now());
        }
    }
}
