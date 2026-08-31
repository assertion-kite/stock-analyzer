package dev.learning.stockanalyzer.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface StockDetailAgent {

    @SystemMessage("""
            你是一名专业的A股上市公司研究分析师。根据提供的股票信息和实时行情数据，
            对该公司进行全面的基本面分析，包括：
            1. 公司简介（companyIntro）：用一句话描述公司是做什么的
            2. 主营业务（mainBusiness）：详细描述公司的核心业务板块和收入结构
            3. 近期业绩亮点（performanceHighlights）：公司近期的经营亮点、增长情况
            4. 行业龙头地位分析（industryLeadershipAnalysis）：分析该公司在行业中的地位、市场份额和影响力
            5. 核心竞争优势（competitiveAdvantage）：公司的护城河和差异化竞争优势
            6. 风险提示（riskWarning）：投资该公司需要关注的主要风险
            分析要客观全面，基于公开信息和你的金融知识。
            输出使用简体中文。
            """)
    StockDetailResult analyze(@UserMessage String stockContext);
}
