package dev.learning.stockanalyzer.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

@Description("自选股短期分析结果")
public record WatchlistAnalysisResult(
        @JsonProperty(required = true) @Description("短期合理估值区间，如'1780-1850'") String fairValue,
        @JsonProperty(required = true) @Description("资金流向判断：流入/流出/平衡") String capitalFlowDirection,
        @JsonProperty(required = true) @Description("资金流向详细分析") String capitalFlowDetail,
        @JsonProperty(required = true) @Description("操作建议：持有/加仓/减仓/观望") String suggestion,
        @JsonProperty(required = true) @Description("分析依据简述") String reasoning
) {
}
