package dev.learning.stockanalyzer.data;

public record StockQuote(String fullCode, String name, double currentPrice, double yesterdayClose,
                         double openPrice, double highPrice, double lowPrice, long volume,
                         double turnover, double changePercent, String dateTime) {
}
