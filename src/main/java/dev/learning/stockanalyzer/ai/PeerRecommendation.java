package dev.learning.stockanalyzer.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("同行业龙头股票推荐结果")
public record PeerRecommendation(
        @JsonProperty(required = true) @Description("推荐的同行业股票列表") List<RecommendedStock> stocks,
        @JsonProperty(required = true) @Description("行业整体分析概述") String industryOverview
) {
    @Description("单只推荐股票")
    public record RecommendedStock(
            @JsonProperty(required = true) @Description("股票代码，如600519") String code,
            @JsonProperty(required = true) @Description("股票名称") String name,
            @JsonProperty(required = true) @Description("推荐理由") String reason,
            @JsonProperty(required = true) @Description("相对优势说明") String advantage
    ) {
    }
}
