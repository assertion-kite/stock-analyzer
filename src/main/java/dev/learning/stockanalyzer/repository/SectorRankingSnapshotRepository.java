package dev.learning.stockanalyzer.repository;

import dev.learning.stockanalyzer.entity.SectorRankingSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SectorRankingSnapshotRepository extends JpaRepository<SectorRankingSnapshotEntity, Long> {
    List<SectorRankingSnapshotEntity> findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(LocalDateTime start, LocalDateTime end);
    List<SectorRankingSnapshotEntity> findBySectorIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(String sectorId, LocalDateTime start, LocalDateTime end);
}
