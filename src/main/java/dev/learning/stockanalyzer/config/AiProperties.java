package dev.learning.stockanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stock.ai")
public class AiProperties {

    private String apiKey;
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String modelName = "glm-4-flash";
    private Duration timeout = Duration.ofSeconds(90);
    private String proxyHost;
    private int proxyPort;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isProxyConfigured() {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort > 0;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
