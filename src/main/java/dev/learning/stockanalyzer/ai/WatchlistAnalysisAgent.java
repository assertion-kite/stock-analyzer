package dev.learning.stockanalyzer.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface WatchlistAnalysisAgent {

    @SystemMessage("""
            你是一名A股短期估值和资金流向分析专家。根据提供的股票实时行情数据，
            对该股票进行短期分析，包括：
            1. 短期合理估值（fairValue）：基于近期走势、行业估值中枢和基本面，给出未来1-2周的合理价格区间
            2. 资金流入流出预估（capitalFlowDirection）：根据成交量、换手率和价格趋势，判断主力资金的可能动向
            3. 操作建议（suggestion）：基于以上分析给出持有/加仓/减仓/观望的简要建议
            分析要客观、保守，明确这是AI参考意见而非投资建议。
            输出使用简体中文。
            """)
    WatchlistAnalysisResult analyze(@UserMessage String stockContext);
}
