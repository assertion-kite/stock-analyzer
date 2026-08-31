package dev.learning.stockanalyzer.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

@Description("股票详情分析结果")
public record StockDetailResult(
        @JsonProperty(required = true) @Description("公司简介，50字以内") String companyIntro,
        @JsonProperty(required = true) @Description("主营业务描述") String mainBusiness,
        @JsonProperty(required = true) @Description("近期业绩亮点") String performanceHighlights,
        @JsonProperty(required = true) @Description("行业龙头地位分析") String industryLeadershipAnalysis,
        @JsonProperty(required = true) @Description("核心竞争优势") String competitiveAdvantage,
        @JsonProperty(required = true) @Description("风险提示") String riskWarning
) {
}
