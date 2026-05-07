package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.MarketAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for market analysis
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/market")
public class MarketController {

    @Autowired
    private MarketAnalysisService marketAnalysisService;

    @GetMapping
    public ResponseEntity<?> getMarketOverview(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            MarketOverviewDTO data = marketAnalysisService.getMarketOverview(dbFile, day);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/kline")
    public ResponseEntity<?> getKlineData(@PathVariable String dbFile) {
        try {
            MarketOverviewDTO data = marketAnalysisService.getMarketOverview(dbFile, 1);
            return ResponseEntity.ok(Map.of("klineData", data.klineData));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/days")
    public ResponseEntity<?> getTotalDays(@PathVariable String dbFile) {
        try {
            int days = marketAnalysisService.getTotalDays(dbFile);
            return ResponseEntity.ok(Map.of("totalDays", days));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/top-stocks")
    public ResponseEntity<?> getTopStocks(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            MarketOverviewDTO data = marketAnalysisService.getMarketOverview(dbFile, day);
            return ResponseEntity.ok(data.topActiveStocks);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
