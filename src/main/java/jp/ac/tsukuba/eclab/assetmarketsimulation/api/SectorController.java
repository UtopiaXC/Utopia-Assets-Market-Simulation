package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.SectorStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.SectorAnalysisService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.SectorAnalysisService.SectorStockInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * REST API for sector statistics
 * Corresponds to Python tabs/sector.py
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/sectors")
public class SectorController {

    @Autowired
    private SectorAnalysisService sectorAnalysisService;

    /**
     * Get all sector statistics
     * GET /api/simulations/{dbFile}/sectors
     */
    @GetMapping
    public ResponseEntity<?> getSectorStats(@PathVariable String dbFile) {
        try {
            SectorStatsDTO data = sectorAnalysisService.getSectorStats(dbFile);
            return ResponseEntity.ok(data);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get list of all sectors
     * GET /api/simulations/{dbFile}/sectors/list
     */
    @GetMapping("/list")
    public ResponseEntity<?> getSectorList(@PathVariable String dbFile) {
        try {
            List<String> sectors = sectorAnalysisService.getSectorList(dbFile);
            return ResponseEntity.ok(sectors);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get stocks in a specific sector
     * GET /api/simulations/{dbFile}/sectors/{sector}/stocks?day=1
     */
    @GetMapping("/{sector}/stocks")
    public ResponseEntity<?> getSectorStocks(
            @PathVariable String dbFile,
            @PathVariable String sector,
            @RequestParam(defaultValue = "1") int day) {
        try {
            List<SectorStockInfo> stocks = sectorAnalysisService.getSectorStocks(dbFile, sector, day);
            return ResponseEntity.ok(stocks);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get market cap history only
     * GET /api/simulations/{dbFile}/sectors/market-cap
     */
    @GetMapping("/market-cap")
    public ResponseEntity<?> getMarketCapHistory(@PathVariable String dbFile) {
        try {
            SectorStatsDTO data = sectorAnalysisService.getSectorStats(dbFile);
            return ResponseEntity.ok(Map.of("marketCap", data.marketCapHistory));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get PE ratio history only
     * GET /api/simulations/{dbFile}/sectors/pe
     */
    @GetMapping("/pe")
    public ResponseEntity<?> getPeHistory(@PathVariable String dbFile) {
        try {
            SectorStatsDTO data = sectorAnalysisService.getSectorStats(dbFile);
            return ResponseEntity.ok(Map.of("pe", data.peHistory));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
