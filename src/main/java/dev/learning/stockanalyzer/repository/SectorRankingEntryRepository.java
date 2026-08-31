package dev.learning.stockanalyzer.repository;

import dev.learning.stockanalyzer.entity.SectorRankingEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SectorRankingEntryRepository extends JpaRepository<SectorRankingEntryEntity, Long> {

    List<SectorRankingEntryEntity> findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(Collection<Long> snapshotIds);
}
