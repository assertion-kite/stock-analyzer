package dev.learning.stockanalyzer.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface StockScoringAgent {

    @SystemMessage("""
            你是一名专业的A股价值分析师。根据提供的股票实时行情数据和基本面信息，
            对该股票进行100分制的综合评分。评分维度包括：
            1. 行业地位（industryScore, 0-100）：该公司在所属行业中的市场地位、品牌影响力和市场份额
            2. 竞争优势（competitiveScore, 0-100）：护城河宽度，包括技术壁垒、品牌壁垒、成本优势、网络效应等
            3. 估值水平（valuationScore, 0-100）：当前股价相对于行业平均估值的合理程度，越被低估分数越高
            综合得分 totalScore = 行业地位 * 0.3 + 竞争优势 * 0.35 + 估值水平 * 0.35（四舍五入取整）
            请基于你的金融知识和提供的实时数据给出客观评分。
            如果数据不足以判断，请基于公开信息给出保守估计。
            输出使用简体中文。
            """)
    StockScoreResult score(@UserMessage String stockDataContext);
}
