package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.SectorStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.SectorAnalysisService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.SectorAnalysisService.SectorStockInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for sector statistics
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/sectors")
public class SectorController {

    @Autowired
    private SectorAnalysisService sectorAnalysisService;

    @GetMapping
    public ResponseEntity<?> getSectorStats(@PathVariable String dbFile) {
        try {
            SectorStatsDTO data = sectorAnalysisService.getSectorStats(dbFile);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> getSectorList(@PathVariable String dbFile) {
        try {
            List<String> sectors = sectorAnalysisService.getSectorList(dbFile);
            return ResponseEntity.ok(sectors);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{sector}/stocks")
    public ResponseEntity<?> getSectorStocks(
            @PathVariable String dbFile,
            @PathVariable String sector,
            @RequestParam(defaultValue = "1") int day) {
        try {
            List<SectorStockInfo> stocks = sectorAnalysisService.getSectorStocks(dbFile, sector, day);
            return ResponseEntity.ok(stocks);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/market-cap")
    public ResponseEntity<?> getMarketCapHistory(@PathVariable String dbFile) {
        try {
            SectorStatsDTO data = sectorAnalysisService.getSectorStats(dbFile);
            return ResponseEntity.ok(Map.of("marketCap", data.marketCapHistory));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pe")
    public ResponseEntity<?> getPeHistory(@PathVariable String dbFile) {
        try {
            SectorStatsDTO data = sectorAnalysisService.getSectorStats(dbFile);
            return ResponseEntity.ok(Map.of("pe", data.peHistory));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
