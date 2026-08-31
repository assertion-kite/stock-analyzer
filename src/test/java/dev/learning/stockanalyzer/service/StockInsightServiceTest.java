package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.ai.StockInsightAgent;
import dev.learning.stockanalyzer.ai.StockInsightResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StockInsightServiceTest {

    private final StockInsightService service = new StockInsightService(
            mock(StockOverviewService.class),
            mock(StockInsightAgent.class),
            new ObjectMapper(),
            mock(StockFundamentalsService.class));

    @Test
    void shouldParseAnswerArrayReturnedByModel() {
        String json = """
                {
                  "headline": "埃斯顿行业地位分析",
                  "answer": ["第一段回答", "第二段回答"],
                  "keyPoints": [{"title":"数据局限","detail":"缺少行业排名数据","stance":"risk"}],
                  "risks": ["缺少财务数据"],
                  "watchItems": ["关注财报"],
                  "followUpQuestions": ["盈利能力如何？"],
                  "disclaimer": "以上为AI辅助分析，不构成投资建议。"
                }
                """;

        StockInsightResult result = service.parseResult(json);

        assertThat(result.answer()).containsExactly("第一段回答", "第二段回答");
        assertThat(result.keyPoints()).hasSize(1);
        assertThat(result.keyPoints().get(0).stance()).isEqualTo("risk");
    }

    @Test
    void shouldAcceptStringAnswerAndMarkdownFence() {
        String response = """
                ```json
                {
                  "headline": "简要结论",
                  "answer": "单段回答也应该兼容",
                  "keyPoints": [],
                  "risks": [],
                  "watchItems": [],
                  "followUpQuestions": []
                }
                ```
                """;

        StockInsightResult result = service.parseResult(response);

        assertThat(result.answer()).isEqualTo(List.of("单段回答也应该兼容"));
        assertThat(result.disclaimer()).isEqualTo("以上为AI辅助分析，不构成投资建议。");
    }
}
