package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingLogResponse;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.StockRankingStatistics;
import dev.learning.stockanalyzer.entity.SectorRankingEntryEntity;
import dev.learning.stockanalyzer.entity.SectorRankingSnapshotEntity;
import dev.learning.stockanalyzer.repository.SectorRankingEntryRepository;
import dev.learning.stockanalyzer.repository.SectorRankingSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectorRankingLogServiceTest {

    private final SectorRankingSnapshotRepository snapshotRepository = mock(SectorRankingSnapshotRepository.class);
    private final SectorRankingEntryRepository entryRepository = mock(SectorRankingEntryRepository.class);
    private final SectorRankingLogService service = new SectorRankingLogService(snapshotRepository, entryRepository);

    @Test
    void shouldAggregatePodiumCountsAndChronologicalRankHistory() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        SectorRankingSnapshotEntity first = snapshot(1L, date.atTime(9, 35));
        SectorRankingSnapshotEntity second = snapshot(2L, date.atTime(10, 5));
        SectorRankingSnapshotEntity third = snapshot(3L, date.atTime(10, 35));
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(List.of(third, second, first));
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(List.of(
                        entry(third, 3),
                        entry(second, 1),
                        entry(first, 2)
                ));

        RankingLogResponse response = service.logs(date, null);
        StockRankingStatistics stock = response.sectors().get(0).stocks().get(0);

        assertThat(response.refreshCount()).isEqualTo(3);
        assertThat(stock.appearances()).isEqualTo(3);
        assertThat(stock.topThreeCount()).isEqualTo(3);
        assertThat(stock.firstCount()).isEqualTo(1);
        assertThat(stock.secondCount()).isEqualTo(1);
        assertThat(stock.thirdCount()).isEqualTo(1);
        assertThat(stock.rankHistory()).containsExactly(2, 1, 3);
        assertThat(stock.averageRank()).isEqualTo(2.0);
    }

    private SectorRankingSnapshotEntity snapshot(long id, LocalDateTime capturedAt) {
        SectorRankingSnapshotEntity snapshot = new SectorRankingSnapshotEntity(
                "gn_bdt", "半导体", capturedAt, capturedAt.toString(), 10);
        ReflectionTestUtils.setField(snapshot, "id", id);
        return snapshot;
    }

    private SectorRankingEntryEntity entry(SectorRankingSnapshotEntity snapshot, int rank) {
        return new SectorRankingEntryEntity(snapshot, rank, "sh600584", "长电科技",
                80.0, 36.0, 2.0);
    }
}
