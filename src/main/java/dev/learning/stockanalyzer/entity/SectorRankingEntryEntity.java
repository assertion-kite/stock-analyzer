package dev.learning.stockanalyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sector_ranking_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_sector_snapshot_rank", columnNames = {"snapshot_id", "rank_no"}),
        indexes = {
                @Index(name = "idx_sector_entry_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_sector_entry_stock", columnList = "full_code")
        })
public class SectorRankingEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private SectorRankingSnapshotEntity snapshot;

    @Column(name = "rank_no", nullable = false)
    private int rank;

    @Column(name = "full_code", nullable = false, length = 12)
    private String fullCode;

    @Column(name = "stock_name", nullable = false, length = 80)
    private String stockName;

    @Column(name = "score")
    private Double score;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(name = "daily_change_percent")
    private Double dailyChangePercent;

    protected SectorRankingEntryEntity() {
    }

    public SectorRankingEntryEntity(SectorRankingSnapshotEntity snapshot, int rank, String fullCode,
                                    String stockName, Double score, Double currentPrice,
                                    Double dailyChangePercent) {
        this.snapshot = snapshot;
        this.rank = rank;
        this.fullCode = fullCode;
        this.stockName = stockName;
        this.score = score;
        this.currentPrice = currentPrice;
        this.dailyChangePercent = dailyChangePercent;
    }

    public Long getId() { return id; }
    public SectorRankingSnapshotEntity getSnapshot() { return snapshot; }
    public int getRank() { return rank; }
    public String getFullCode() { return fullCode; }
    public String getStockName() { return stockName; }
    public Double getScore() { return score; }
    public Double getCurrentPrice() { return currentPrice; }
    public Double getDailyChangePercent() { return dailyChangePercent; }
}
