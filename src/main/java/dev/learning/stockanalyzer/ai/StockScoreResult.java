package dev.learning.stockanalyzer.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

@Description("股票综合评分结果")
public record StockScoreResult(
        @JsonProperty(required = true) @Description("行业地位得分，0到100") int industryScore,
        @JsonProperty(required = true) @Description("竞争优势得分，0到100") int competitiveScore,
        @JsonProperty(required = true) @Description("估值水平得分，0到100") int valuationScore,
        @JsonProperty(required = true) @Description("综合得分，0到100") int totalScore,
        @JsonProperty(required = true) @Description("行业地位评价") String industryComment,
        @JsonProperty(required = true) @Description("竞争优势评价") String competitiveComment,
        @JsonProperty(required = true) @Description("估值水平评价") String valuationComment,
        @JsonProperty(required = true) @Description("综合投资评语") String summary
) {
}
