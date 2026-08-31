package dev.learning.stockanalyzer.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SectorRankingLogModels {
    private SectorRankingLogModels() {}
    public record RankingEntry(int rank, String fullCode, String stockName, Double score, Double currentPrice, Double dailyChangePercent) {}
    public record RankingSnapshot(Long id, String sectorId, String sectorName, LocalDateTime capturedAt, String sourceFetchedAt, List<RankingEntry> stocks) {}
    public record StockRankingStatistics(String fullCode, String stockName, int appearances, int topThreeCount, int firstCount, int secondCount, int thirdCount, int bestRank, double averageRank, List<Integer> rankHistory) {}
    public record SectorRankingStatistics(String sectorId, String sectorName, int refreshCount, List<StockRankingStatistics> stocks) {}
    public record RankingLogResponse(LocalDate date, int refreshCount, List<SectorRankingStatistics> sectors, List<RankingSnapshot> snapshots) {}
}
