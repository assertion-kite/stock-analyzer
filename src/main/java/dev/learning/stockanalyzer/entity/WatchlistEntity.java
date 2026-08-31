package dev.learning.stockanalyzer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist")
public class WatchlistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_code", nullable = false, unique = true, length = 10)
    private String fullCode;

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Column(name = "market", nullable = false, length = 2)
    private String market;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "industry", length = 50)
    private String industry;

    @Column(name = "added_time", nullable = false)
    private LocalDateTime addedTime;

    @Column(name = "notes", length = 500)
    private String notes;

    public WatchlistEntity() {}

    public WatchlistEntity(String fullCode, String code, String market, String name, String industry) {
        this.fullCode = fullCode;
        this.code = code;
        this.market = market;
        this.name = name;
        this.industry = industry;
        this.addedTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullCode() { return fullCode; }
    public void setFullCode(String fullCode) { this.fullCode = fullCode; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public LocalDateTime getAddedTime() { return addedTime; }
    public void setAddedTime(LocalDateTime addedTime) { this.addedTime = addedTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
