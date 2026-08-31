package dev.learning.stockanalyzer.data;

import java.util.Locale;

public final class StockCodeUtils {
    private StockCodeUtils() {}

    public static String normalizeFullCode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("股票代码不能为空");
        String raw = value.trim().toLowerCase(Locale.ROOT);
        if (raw.matches("(?:sh|sz)\\d{6}")) return raw;
        if (raw.matches("\\d{6}")) {
            String market = raw.startsWith("6") ? "sh" : "sz";
            return market + raw;
        }
        throw new IllegalArgumentException("股票代码格式不正确: " + value);
    }
}
