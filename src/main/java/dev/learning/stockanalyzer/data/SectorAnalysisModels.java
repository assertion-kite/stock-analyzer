package dev.learning.stockanalyzer.data;

import java.util.List;

public final class SectorAnalysisModels {
    private SectorAnalysisModels() {}
    public record SectorSummary(String id, String name, String type, Integer companyCount, Double changePercent,
                                Double totalVolume, Double totalAmount, Double netInflow, Double netInflowRatio,
                                String leaderCode, String leaderName, Double leaderPrice, Double leaderChangePercent,
                                Double score, String status) {}
    public record SectorListResponse(boolean available, boolean flowAvailable, List<SectorSummary> sectors,
                                     String source, List<String> warnings, String fetchedAt) {
        public static SectorListResponse unavailable(String warning) { return new SectorListResponse(false, false, List.of(), null, List.of(warning), null); }
    }
    public record SectorSearchResponse(List<SectorSummary> results, List<String> warnings, String fetchedAt) {
        public static SectorSearchResponse unavailable(String warning) { return new SectorSearchResponse(List.of(), List.of(warning), null); }
    }
    public record IntradayPoint(String time, Double price, Double volume) {}
    public record QuarterlyPerformance(String period, String reportDate, Double revenue, Double netProfit, String source) {}
    public record ResearchReport(String title, String rating, String institution, String date, String industry,
                                 String pdfUrl, java.util.Map<String, Double> epsForecasts, java.util.Map<String, Double> peForecasts) {}
    public record IntradayStrength(String fullCode, String code, String name, Double currentPrice,
                                   Double dailyChangePercent, Double amount, Double turnoverRate, Double pe,
                                   Double pb, Double marketValue, boolean selected, String warning,
                                   String leaderReason, String performanceLabel, Double return1m, Double return3m,
                                   Double return5m, Double amplitude1m, Double amplitude3m, Double amplitude5m,
                                   Double volumeRatio, boolean volumeExpanded, List<IntradayPoint> points,
                                   String minuteDataTime, Double mainNetInflow, Double mainNetRatio, String flowDate,
                                   List<QuarterlyPerformance> quarterlyPerformance, ResearchReport latestReport,
                                   Double relativeStrength, Double score, List<String> signals, int rank,
                                   String strengthLabel) {
        public IntradayStrength { points = points == null ? List.of() : List.copyOf(points); quarterlyPerformance = quarterlyPerformance == null ? List.of() : List.copyOf(quarterlyPerformance); signals = signals == null ? List.of() : List.copyOf(signals); }
    }
    public record SectorDetailResponse(boolean available, SectorSummary sector, String selectedCode,
                                       List<IntradayStrength> stocks, String formula, String source,
                                       List<String> warnings, String fetchedAt) {
        public static SectorDetailResponse unavailable(String selectedCode, String warning) { return new SectorDetailResponse(false, null, selectedCode, List.of(), null, null, List.of(warning), null); }
    }
}
