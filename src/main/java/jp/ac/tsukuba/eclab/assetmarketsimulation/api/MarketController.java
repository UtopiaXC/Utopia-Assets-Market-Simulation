package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.MarketAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

/**
 * REST API for market analysis
 * Corresponds to Python tabs/market.py
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/market")
public class MarketController {

    @Autowired
    private MarketAnalysisService marketAnalysisService;

    /**
     * Get market overview data
     * GET /api/simulations/{dbFile}/market?day=1
     */
    @GetMapping
    public ResponseEntity<?> getMarketOverview(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            MarketOverviewDTO data = marketAnalysisService.getMarketOverview(dbFile, day);
            return ResponseEntity.ok(data);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get K-line data only
     * GET /api/simulations/{dbFile}/market/kline
     */
    @GetMapping("/kline")
    public ResponseEntity<?> getKlineData(@PathVariable String dbFile) {
        try {
            MarketOverviewDTO data = marketAnalysisService.getMarketOverview(dbFile, 1);
            return ResponseEntity.ok(Map.of("klineData", data.klineData));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get total simulation days
     * GET /api/simulations/{dbFile}/market/days
     */
    @GetMapping("/days")
    public ResponseEntity<?> getTotalDays(@PathVariable String dbFile) {
        try {
            int days = marketAnalysisService.getTotalDays(dbFile);
            return ResponseEntity.ok(Map.of("totalDays", days));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get top active stocks for a specific day
     * GET /api/simulations/{dbFile}/market/top-stocks?day=1
     */
    @GetMapping("/top-stocks")
    public ResponseEntity<?> getTopStocks(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            MarketOverviewDTO data = marketAnalysisService.getMarketOverview(dbFile, day);
            return ResponseEntity.ok(data.topActiveStocks);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
