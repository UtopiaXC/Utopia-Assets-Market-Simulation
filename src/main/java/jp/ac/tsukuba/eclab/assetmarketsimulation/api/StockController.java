package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StockDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.StockAnalysisService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.StockAnalysisService.StockSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * REST API for stock analysis
 * Corresponds to Python tabs/stock.py
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/stocks")
public class StockController {

    @Autowired
    private StockAnalysisService stockAnalysisService;

    /**
     * Get list of all stocks for a given day
     * GET /api/simulations/{dbFile}/stocks?day=1
     */
    @GetMapping
    public ResponseEntity<?> getStockList(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            List<StockSummary> stocks = stockAnalysisService.getStockList(dbFile, day);
            return ResponseEntity.ok(stocks);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get detailed stock analysis
     * GET /api/simulations/{dbFile}/stocks/{stockId}?day=1
     */
    @GetMapping("/{stockId}")
    public ResponseEntity<?> getStockDetail(
            @PathVariable String dbFile,
            @PathVariable String stockId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            StockDetailDTO detail = stockAnalysisService.getStockDetail(dbFile, stockId, day);
            return ResponseEntity.ok(detail);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get stock history (for charts)
     * GET /api/simulations/{dbFile}/stocks/{stockId}/history
     */
    @GetMapping("/{stockId}/history")
    public ResponseEntity<?> getStockHistory(
            @PathVariable String dbFile,
            @PathVariable String stockId) {
        try {
            StockDetailDTO detail = stockAnalysisService.getStockDetail(dbFile, stockId, 1);
            return ResponseEntity.ok(Map.of("history", detail.history));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get stock shareholders
     * GET /api/simulations/{dbFile}/stocks/{stockId}/shareholders?day=1
     */
    @GetMapping("/{stockId}/shareholders")
    public ResponseEntity<?> getStockShareholders(
            @PathVariable String dbFile,
            @PathVariable String stockId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            StockDetailDTO detail = stockAnalysisService.getStockDetail(dbFile, stockId, day);
            return ResponseEntity.ok(detail.shareholders);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
