package dev.learning.stockanalyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sector_ranking_snapshot", indexes = {
        @Index(name = "idx_sector_snapshot_time", columnList = "captured_at"),
        @Index(name = "idx_sector_snapshot_sector_time", columnList = "sector_id,captured_at")
})
public class SectorRankingSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sector_id", nullable = false, length = 80)
    private String sectorId;

    @Column(name = "sector_name", nullable = false, length = 80)
    private String sectorName;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(name = "source_fetched_at", length = 40)
    private String sourceFetchedAt;

    @Column(name = "stock_count", nullable = false)
    private int stockCount;

    protected SectorRankingSnapshotEntity() {
    }

    public SectorRankingSnapshotEntity(String sectorId, String sectorName, LocalDateTime capturedAt,
                                       String sourceFetchedAt, int stockCount) {
        this.sectorId = sectorId;
        this.sectorName = sectorName;
        this.capturedAt = capturedAt;
        this.sourceFetchedAt = sourceFetchedAt;
        this.stockCount = stockCount;
    }

    public Long getId() { return id; }
    public String getSectorId() { return sectorId; }
    public String getSectorName() { return sectorName; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public String getSourceFetchedAt() { return sourceFetchedAt; }
    public int getStockCount() { return stockCount; }
}
