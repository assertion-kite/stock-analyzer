package dev.learning.stockanalyzer.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record StockFundamentalsSnapshot(String fullCode, boolean available, Profile profile,
                                        List<PerformancePeriod> performance, ValuationSnapshot valuation,
                                        List<ResearchReport> researchReports, IndustryPosition industryPosition,
                                        List<String> concepts, List<SourceStatus> sources,
                                        List<String> warnings, String fetchedAt) {
    public StockFundamentalsSnapshot {
        performance = performance == null ? List.of() : List.copyOf(performance);
        researchReports = researchReports == null ? List.of() : List.copyOf(researchReports);
        concepts = concepts == null ? List.of() : List.copyOf(concepts);
        sources = sources == null ? List.of() : List.copyOf(sources);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static StockFundamentalsSnapshot unavailable(String fullCode, String warning) {
        return new StockFundamentalsSnapshot(fullCode, false, null, List.of(), null, List.of(), null,
                List.of(), List.of(), List.of(warning), null);
    }

    public StockFundamentalsSnapshot withWarning(String warning) {
        List<String> next = new ArrayList<>(warnings);
        if (warning != null && !next.contains(warning)) next.add(warning);
        return new StockFundamentalsSnapshot(fullCode, available, profile, performance, valuation,
                researchReports, industryPosition, concepts, sources, next, fetchedAt);
    }

    public record Profile(String companyName, String industry, String listDate, Double totalMarketValue,
                          Double floatMarketValue, String mainBusiness, String productTypes,
                          String productNames, String businessScope, String introduction) {}
    public record PerformancePeriod(String reportDate, String reportName, Double revenue, Double revenueYoY,
                                    Double netProfit, Double netProfitYoY, Double adjustedNetProfit,
                                    Double adjustedNetProfitYoY, Double eps, Double roe, Double grossMargin,
                                    Double debtRatio, Double operatingCashFlow, String source) {}
    public record ValuationSnapshot(String date, Double closePrice, Double totalMarketValue, Double peTtm,
                                    Double peStatic, Double pb, Double peg, Double ps, Double pcf,
                                    Double industryPeTtm, Double industryPb, String peRank,
                                    List<PeerValuation> peers) {
        public ValuationSnapshot { peers = peers == null ? List.of() : List.copyOf(peers); }
    }
    public record PeerValuation(String code, String name, Double peTtm, Double pb, Double peg, String rank) {}
    public record ResearchReport(String title, String rating, String institution, String date, String industry,
                                 String pdfUrl, Map<String, Double> epsForecasts, Map<String, Double> peForecasts) {}
    public record IndustryPosition(String industry, String valuationRank, String growthRank, String roeRank,
                                   String scaleRank, String summary) {}
    public record SourceStatus(String name, boolean available, String message, String updatedAt) {}
}
