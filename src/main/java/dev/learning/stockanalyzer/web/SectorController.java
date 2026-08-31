package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorDetailResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorListResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorSearchResponse;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingLogResponse;
import dev.learning.stockanalyzer.service.SectorAnalysisService;
import dev.learning.stockanalyzer.service.SectorRankingLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sectors")
public class SectorController {

    private final SectorAnalysisService sectorAnalysisService;
    private final SectorRankingLogService rankingLogService;

    public SectorController(SectorAnalysisService sectorAnalysisService,
                            SectorRankingLogService rankingLogService) {
        this.sectorAnalysisService = sectorAnalysisService;
        this.rankingLogService = rankingLogService;
    }

    @GetMapping
    public SectorListResponse list(@RequestParam(defaultValue = "false") boolean refresh) {
        return sectorAnalysisService.list(refresh);
    }

    @GetMapping("/search")
    public SectorSearchResponse search(@RequestParam String keyword) {
        return sectorAnalysisService.search(keyword);
    }

    @GetMapping("/stock/{fullCode}")
    public SectorDetailResponse stock(
            @PathVariable String fullCode,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return sectorAnalysisService.stock(fullCode, refresh);
    }

    @GetMapping("/ranking-logs")
    public RankingLogResponse rankingLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String sectorId) {
        return rankingLogService.logs(date, sectorId);
    }

    @GetMapping("/{sectorId}")
    public SectorDetailResponse detail(
            @PathVariable String sectorId,
            @RequestParam(required = false) String selected,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return sectorAnalysisService.detail(sectorId, selected, refresh);
    }
}
