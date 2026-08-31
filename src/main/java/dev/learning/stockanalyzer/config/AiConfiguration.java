package dev.learning.stockanalyzer.config;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.learning.stockanalyzer.ai.MarketDailyPicksAgent;
import dev.learning.stockanalyzer.ai.PeerRecommendationAgent;
import dev.learning.stockanalyzer.ai.StockDetailAgent;
import dev.learning.stockanalyzer.ai.StockScoringAgent;
import dev.learning.stockanalyzer.ai.StockInsightAgent;
import dev.learning.stockanalyzer.ai.WatchlistAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    ChatModel chatModel(AiProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("未配置 ZHIPU_API_KEY，行情功能可用，AI研判将在调用时提示配置");
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    throw new IllegalStateException("AI服务尚未配置，请先设置 ZHIPU_API_KEY 环境变量");
                }
            };
        }

        var modelBuilder = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey().trim())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(0.3)
                .timeout(properties.getTimeout())
                .maxRetries(0)
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false);

        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectTimeout(properties.getTimeout())
                .readTimeout(properties.getTimeout());

        if (properties.isProxyConfigured()) {
            HttpClient.Builder javaHttpClientBuilder = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(new InetSocketAddress(
                            properties.getProxyHost().trim(),
                            properties.getProxyPort())));
            httpClientBuilder.httpClientBuilder(javaHttpClientBuilder);
            log.info("AI HTTP代理已启用 proxy={}:{}",
                    properties.getProxyHost(), properties.getProxyPort());
        }

        ChatModel model = modelBuilder.httpClientBuilder(httpClientBuilder).build();
        log.info("GLM AI已启用 model={} baseUrl={}",
                properties.getModelName(), properties.getBaseUrl());
        return model;
    }

    @Bean
    StockScoringAgent stockScoringAgent(ChatModel chatModel) {
        return AiServices.builder(StockScoringAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    PeerRecommendationAgent peerRecommendationAgent(ChatModel chatModel) {
        return AiServices.builder(PeerRecommendationAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    MarketDailyPicksAgent marketDailyPicksAgent(ChatModel chatModel) {
        return AiServices.builder(MarketDailyPicksAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    WatchlistAnalysisAgent watchlistAnalysisAgent(ChatModel chatModel) {
        return AiServices.builder(WatchlistAnalysisAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    StockDetailAgent stockDetailAgent(ChatModel chatModel) {
        return AiServices.builder(StockDetailAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    StockInsightAgent stockInsightAgent(ChatModel chatModel) {
        return AiServices.builder(StockInsightAgent.class)
                .chatModel(chatModel)
                .build();
    }
}
