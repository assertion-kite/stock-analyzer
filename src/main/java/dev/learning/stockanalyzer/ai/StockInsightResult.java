package dev.learning.stockanalyzer.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("针对用户问题的股票研判结果")
public record StockInsightResult(
        @JsonProperty(required = true) @Description("直接回应用户问题的一句话标题") String headline,
        @JsonProperty(required = true) @Description("自然、具体的核心回答，每个元素为一段") List<String> answer,
        @JsonProperty(required = true) @Description("关键观察列表，数量根据问题决定") List<InsightPoint> keyPoints,
        @JsonProperty(required = true) @Description("当前必须注意的风险或数据局限") List<String> risks,
        @JsonProperty(required = true) @Description("后续应跟踪的客观指标") List<String> watchItems,
        @JsonProperty(required = true) @Description("可继续向AI追问的三个问题") List<String> followUpQuestions,
        @JsonProperty(required = true) @Description("免责声明") String disclaimer
) {
    @Description("一项关键观察")
    public record InsightPoint(
            @JsonProperty(required = true) @Description("观察标题") String title,
            @JsonProperty(required = true) @Description("观察说明") String detail,
            @JsonProperty(required = true) @Description("倾向：positive、neutral或risk") String stance
    ) {
    }
}
