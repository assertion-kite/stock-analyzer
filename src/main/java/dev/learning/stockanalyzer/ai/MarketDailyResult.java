package dev.learning.stockanalyzer.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("每日市场精选推荐")
public record MarketDailyResult(
        @JsonProperty(required = true) @Description("当日市场环境总结") String marketSummary,
        @JsonProperty(required = true) @Description("推荐关注的龙头股票") List<DailyPick> picks,
        @JsonProperty(required = true) @Description("风险提示") String disclaimer
) {
    @Description("每日推荐个股")
    public record DailyPick(
            @JsonProperty(required = true) @Description("股票代码") String code,
            @JsonProperty(required = true) @Description("股票名称") String name,
            @JsonProperty(required = true) @Description("所属行业") String industry,
            @JsonProperty(required = true) @Description("推荐逻辑") String logic,
            @JsonProperty(required = true) @Description("关注价格区间") String priceRange
    ) {
    }
}
