package dev.learning.stockanalyzer.data;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StockDataService {
    private static final Pattern SINA = Pattern.compile("hq_str_([a-z]{2}\\d{6})=\\\"([^\\\"]*)\\\"");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public StockQuote getQuote(String code) {
        List<StockQuote> quotes = getQuotes(List.of(code));
        if (quotes.isEmpty()) throw new IllegalStateException("行情暂不可用: " + code);
        return quotes.get(0);
    }

    public List<StockQuote> getQuotes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        List<String> normalized = codes.stream().map(StockCodeUtils::normalizeFullCode).distinct().toList();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://hq.sinajs.cn/list=" + String.join(",", normalized)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Referer", "https://finance.sina.com.cn")
                    .GET().build();
            String body = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            Map<String, StockQuote> parsed = parseSina(body);
            return normalized.stream().map(parsed::get).filter(java.util.Objects::nonNull).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    Map<String, StockQuote> parseSina(String body) {
        Map<String, StockQuote> result = new java.util.LinkedHashMap<>();
        Matcher matcher = SINA.matcher(body == null ? "" : body);
        while (matcher.find()) {
            String[] values = matcher.group(2).split(",", -1);
            if (values.length < 32) continue;
            try {
                String code = matcher.group(1).toLowerCase();
                double yesterday = number(values, 2), current = number(values, 3);
                double change = yesterday == 0 ? 0 : (current - yesterday) / yesterday * 100;
                result.put(code, new StockQuote(code, values[0], current, yesterday, number(values, 5),
                        number(values, 4), number(values, 6), (long) number(values, 8), number(values, 9),
                        change, values[30] + " " + values[31]));
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    private double number(String[] values, int index) {
        return index < values.length && !values[index].isBlank() ? Double.parseDouble(values[index]) : 0;
    }
}
