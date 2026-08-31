package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.config.FundamentalsProperties;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorDetailResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorListResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorSearchResponse;
import dev.learning.stockanalyzer.data.StockCodeUtils;
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

@Service
public class SectorAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SectorAnalysisService.class);

    private final FundamentalsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SectorRankingLogService rankingLogService;

    @Autowired
    public SectorAnalysisService(FundamentalsProperties properties, ObjectMapper objectMapper,
                                 SectorRankingLogService rankingLogService) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build(), rankingLogService);
    }

    SectorAnalysisService(FundamentalsProperties properties,
                          ObjectMapper objectMapper,
                          HttpClient httpClient,
                          SectorRankingLogService rankingLogService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.rankingLogService = rankingLogService;
    }

    public SectorListResponse list(boolean refresh) {
        if (!properties.isEnabled()) {
            return SectorListResponse.unavailable("板块数据服务已停用");
        }
        try {
            return objectMapper.readValue(
                    get("/api/sectors?refresh=" + refresh), SectorListResponse.class);
        } catch (Exception e) {
            log.warn("获取板块主线列表失败", e);
            return SectorListResponse.unavailable("板块数据暂不可用，请确认 AKShare 服务已启动");
        }
    }

    public SectorSearchResponse search(String keyword) {
        if (!properties.isEnabled()) {
            return SectorSearchResponse.unavailable("板块数据服务已停用");
        }
        if (keyword == null || keyword.isBlank()) {
            return new SectorSearchResponse(java.util.List.of(), java.util.List.of(), null);
        }
        try {
            return objectMapper.readValue(
                    get("/api/sectors/search?keyword=" + pathSegment(keyword.trim())),
                    SectorSearchResponse.class);
        } catch (Exception e) {
            log.warn("搜索板块失败: {}", keyword, e);
            return SectorSearchResponse.unavailable("板块搜索暂不可用");
        }
    }

    public SectorDetailResponse detail(String sectorId, String selectedCode, boolean refresh) {
        String path = "/api/sectors/" + pathSegment(sectorId)
                + "?refresh=" + refresh
                + (selectedCode == null || selectedCode.isBlank()
                ? ""
                : "&selected=" + pathSegment(StockCodeUtils.normalizeFullCode(selectedCode)));
        return loadDetail(path, selectedCode);
    }

    public SectorDetailResponse stock(String code, boolean refresh) {
        String fullCode = StockCodeUtils.normalizeFullCode(code);
        return loadDetail(
                "/api/sectors/stock/" + fullCode + "?refresh=" + refresh,
                fullCode);
    }

    private SectorDetailResponse loadDetail(String path, String selectedCode) {
        if (!properties.isEnabled()) {
            return SectorDetailResponse.unavailable(selectedCode, "板块数据服务已停用");
        }
        try {
            SectorDetailResponse response = objectMapper.readValue(get(path), SectorDetailResponse.class);
            try {
                rankingLogService.record(response);
            } catch (RuntimeException persistenceError) {
                log.warn("保存板块 Top10 排名快照失败: {}", path, persistenceError);
            }
            return response;
        } catch (Exception e) {
            log.warn("获取板块分时强度失败: {}", path, e);
            return SectorDetailResponse.unavailable(
                    selectedCode, "板块强度数据暂不可用，请稍后重试");
        }
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + path))
                .timeout(properties.getTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("AKShare板块服务状态异常: " + response.statusCode());
        }
        return response.body();
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
