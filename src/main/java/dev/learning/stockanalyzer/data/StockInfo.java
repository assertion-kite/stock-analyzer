package dev.learning.stockanalyzer.data;

public record StockInfo(String code, String market, String name, String industry) {
    public String fullCode() {
        return (market == null ? "" : market.toLowerCase()) + code;
    }
}
