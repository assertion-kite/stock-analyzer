package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.SectorAnalysisModels.IntradayStrength;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorDetailResponse;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingEntry;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingLogResponse;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingSnapshot;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.SectorRankingStatistics;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.StockRankingStatistics;
import dev.learning.stockanalyzer.entity.SectorRankingEntryEntity;
import dev.learning.stockanalyzer.entity.SectorRankingSnapshotEntity;
import dev.learning.stockanalyzer.repository.SectorRankingEntryRepository;
import dev.learning.stockanalyzer.repository.SectorRankingSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectorRankingLogService {

    private final SectorRankingSnapshotRepository snapshotRepository;
    private final SectorRankingEntryRepository entryRepository;

    public SectorRankingLogService(SectorRankingSnapshotRepository snapshotRepository,
                                   SectorRankingEntryRepository entryRepository) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public void record(SectorDetailResponse response) {
        if (response == null || !response.available() || response.sector() == null
                || response.stocks() == null || response.stocks().isEmpty()) {
            return;
        }
        List<IntradayStrength> topTen = response.stocks().stream()
                .filter(stock -> stock.fullCode() != null && stock.name() != null)
                .sorted(Comparator.comparingInt(IntradayStrength::rank))
                .limit(10)
                .toList();
        if (topTen.isEmpty()) return;

        SectorRankingSnapshotEntity snapshot = snapshotRepository.save(new SectorRankingSnapshotEntity(
                response.sector().id(),
                response.sector().name(),
                LocalDateTime.now(),
                response.fetchedAt(),
                topTen.size()
        ));
        List<SectorRankingEntryEntity> entries = new ArrayList<>(topTen.size());
        for (int index = 0; index < topTen.size(); index++) {
            IntradayStrength stock = topTen.get(index);
            int rank = stock.rank() > 0 ? stock.rank() : index + 1;
            entries.add(new SectorRankingEntryEntity(
                    snapshot, rank, stock.fullCode(), stock.name(), stock.score(),
                    stock.currentPrice(), stock.dailyChangePercent()));
        }
        entryRepository.saveAll(entries);
    }

    @Transactional(readOnly = true)
    public RankingLogResponse logs(LocalDate date, String sectorId) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();
        List<SectorRankingSnapshotEntity> snapshots = sectorId == null || sectorId.isBlank()
                ? snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(start, end)
                : snapshotRepository.findBySectorIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
                        sectorId, start, end);
        if (snapshots.isEmpty()) return new RankingLogResponse(targetDate, 0, List.of(), List.of());

        List<Long> snapshotIds = snapshots.stream().map(SectorRankingSnapshotEntity::getId).toList();
        List<SectorRankingEntryEntity> entries = entryRepository
                .findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(snapshotIds);
        Map<Long, List<SectorRankingEntryEntity>> entriesBySnapshot = new HashMap<>();
        entries.forEach(entry -> entriesBySnapshot
                .computeIfAbsent(entry.getSnapshot().getId(), ignored -> new ArrayList<>())
                .add(entry));

        List<RankingSnapshot> snapshotResults = snapshots.stream()
                .map(snapshot -> toSnapshot(snapshot, entriesBySnapshot.getOrDefault(snapshot.getId(), List.of())))
                .toList();
        Map<String, List<SectorRankingSnapshotEntity>> bySector = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> bySector
                .computeIfAbsent(snapshot.getSectorId(), ignored -> new ArrayList<>())
                .add(snapshot));
        List<SectorRankingStatistics> statistics = bySector.values().stream()
                .map(group -> aggregateSector(group, entriesBySnapshot))
                .sorted(Comparator.comparingInt(SectorRankingStatistics::refreshCount).reversed()
                        .thenComparing(SectorRankingStatistics::sectorName))
                .toList();
        return new RankingLogResponse(targetDate, snapshots.size(), statistics, snapshotResults);
    }

    private RankingSnapshot toSnapshot(SectorRankingSnapshotEntity snapshot,
                                       List<SectorRankingEntryEntity> entries) {
        List<RankingEntry> stocks = entries.stream()
                .sorted(Comparator.comparingInt(SectorRankingEntryEntity::getRank))
                .map(entry -> new RankingEntry(
                        entry.getRank(), entry.getFullCode(), entry.getStockName(), entry.getScore(),
                        entry.getCurrentPrice(), entry.getDailyChangePercent()))
                .toList();
        return new RankingSnapshot(snapshot.getId(), snapshot.getSectorId(), snapshot.getSectorName(),
                snapshot.getCapturedAt(), snapshot.getSourceFetchedAt(), stocks);
    }

    private SectorRankingStatistics aggregateSector(
            List<SectorRankingSnapshotEntity> snapshots,
            Map<Long, List<SectorRankingEntryEntity>> entriesBySnapshot) {
        Map<String, MutableStockStatistics> byStock = new LinkedHashMap<>();
        snapshots.stream()
                .sorted(Comparator.comparing(SectorRankingSnapshotEntity::getCapturedAt))
                .forEach(snapshot -> entriesBySnapshot.getOrDefault(snapshot.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(SectorRankingEntryEntity::getRank))
                        .forEach(entry -> byStock.computeIfAbsent(entry.getFullCode(), ignored ->
                                        new MutableStockStatistics(entry.getFullCode(), entry.getStockName()))
                                .add(entry.getRank())));
        List<StockRankingStatistics> stocks = byStock.values().stream()
                .map(MutableStockStatistics::toResult)
                .sorted(Comparator.comparingInt(StockRankingStatistics::topThreeCount).reversed()
                        .thenComparing(Comparator.comparingInt(StockRankingStatistics::firstCount).reversed())
                        .thenComparingDouble(StockRankingStatistics::averageRank)
                        .thenComparing(StockRankingStatistics::stockName))
                .toList();
        SectorRankingSnapshotEntity first = snapshots.get(0);
        return new SectorRankingStatistics(first.getSectorId(), first.getSectorName(), snapshots.size(), stocks);
    }

    private static final class MutableStockStatistics {
        private final String fullCode;
        private final String stockName;
        private final List<Integer> ranks = new ArrayList<>();

        private MutableStockStatistics(String fullCode, String stockName) {
            this.fullCode = fullCode;
            this.stockName = stockName;
        }

        private void add(int rank) { ranks.add(rank); }

        private StockRankingStatistics toResult() {
            int first = (int) ranks.stream().filter(rank -> rank == 1).count();
            int second = (int) ranks.stream().filter(rank -> rank == 2).count();
            int third = (int) ranks.stream().filter(rank -> rank == 3).count();
            int topThree = (int) ranks.stream().filter(rank -> rank <= 3).count();
            int best = ranks.stream().mapToInt(Integer::intValue).min().orElse(0);
            double average = ranks.stream().mapToInt(Integer::intValue).average().orElse(0);
            return new StockRankingStatistics(fullCode, stockName, ranks.size(), topThree,
                    first, second, third, best, Math.round(average * 100.0) / 100.0, List.copyOf(ranks));
        }
    }
}
