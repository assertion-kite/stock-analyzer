package dev.learning.stockanalyzer.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface MarketDailyPicksAgent {

    @SystemMessage("""
            你是一名A股市场每日策略分析师。根据提供的当日市场概况数据（包括主要指数表现和部分个股行情），
            分析当前市场环境，并推荐3到5只适合短期关注的行业龙头股票。
            推荐标准：
            1. 所属板块或行业当日表现活跃或具有轮动机会
            2. 公司是该行业公认龙头，基本面扎实
            3. 当前估值处于合理区间，具有短期上行空间
            给出市场环境总结和每只推荐股票的推荐逻辑。
            必须在disclaimer中声明：以上为AI参考分析，不构成投资建议。
            输出使用简体中文。
            """)
    MarketDailyResult dailyPicks(@UserMessage String marketOverview);
}
