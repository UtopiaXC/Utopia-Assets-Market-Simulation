package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.TraderDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.TraderAnalysisService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.TraderAnalysisService.TraderSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * REST API for trader analysis
 * Corresponds to Python tabs/trader.py
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/traders")
public class TraderController {

    @Autowired
    private TraderAnalysisService traderAnalysisService;

    /**
     * Get list of all traders for a given day
     * GET /api/simulations/{dbFile}/traders?day=1
     */
    @GetMapping
    public ResponseEntity<?> getTraderList(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            List<TraderSummary> traders = traderAnalysisService.getTraderList(dbFile, day);
            return ResponseEntity.ok(traders);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get detailed trader analysis
     * GET /api/simulations/{dbFile}/traders/{traderId}?day=1
     */
    @GetMapping("/{traderId}")
    public ResponseEntity<?> getTraderDetail(
            @PathVariable String dbFile,
            @PathVariable int traderId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            TraderDetailDTO detail = traderAnalysisService.getTraderDetail(dbFile, traderId, day);
            return ResponseEntity.ok(detail);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get trader history (for charts)
     * GET /api/simulations/{dbFile}/traders/{traderId}/history
     */
    @GetMapping("/{traderId}/history")
    public ResponseEntity<?> getTraderHistory(
            @PathVariable String dbFile,
            @PathVariable int traderId) {
        try {
            TraderDetailDTO detail = traderAnalysisService.getTraderDetail(dbFile, traderId, 1);
            return ResponseEntity.ok(Map.of("history", detail.history));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get trader holdings for a specific day
     * GET /api/simulations/{dbFile}/traders/{traderId}/holdings?day=1
     */
    @GetMapping("/{traderId}/holdings")
    public ResponseEntity<?> getTraderHoldings(
            @PathVariable String dbFile,
            @PathVariable int traderId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            TraderDetailDTO detail = traderAnalysisService.getTraderDetail(dbFile, traderId, day);
            return ResponseEntity.ok(detail.holdings);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
