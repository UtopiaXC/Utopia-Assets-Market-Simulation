package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StockDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.TradeRecordEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.TradeRecordMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.DatabaseService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.StockAnalysisService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.StockAnalysisService.StockSummary;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for stock analysis
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/stocks")
public class StockController {

    @Autowired
    private StockAnalysisService stockAnalysisService;

    @Autowired
    private DatabaseService databaseService;

    @GetMapping
    public ResponseEntity<?> getStockList(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            List<StockSummary> stocks = stockAnalysisService.getStockList(dbFile, day);
            return ResponseEntity.ok(stocks);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{stockId}")
    public ResponseEntity<?> getStockDetail(
            @PathVariable String dbFile,
            @PathVariable String stockId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            StockDetailDTO detail = stockAnalysisService.getStockDetail(dbFile, stockId, day);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{stockId}/history")
    public ResponseEntity<?> getStockHistory(
            @PathVariable String dbFile,
            @PathVariable String stockId) {
        try {
            StockDetailDTO detail = stockAnalysisService.getStockDetail(dbFile, stockId, 1);
            return ResponseEntity.ok(Map.of("history", detail.history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{stockId}/shareholders")
    public ResponseEntity<?> getStockShareholders(
            @PathVariable String dbFile,
            @PathVariable String stockId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            StockDetailDTO detail = stockAnalysisService.getStockDetail(dbFile, stockId, day);
            return ResponseEntity.ok(detail.shareholders);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * NEW: Get trade records for a specific stock on a specific day
     * GET /api/simulations/{dbFile}/stocks/{stockId}/trades?day=1
     */
    @GetMapping("/{stockId}/trades")
    public ResponseEntity<?> getStockTrades(
            @PathVariable String dbFile,
            @PathVariable String stockId,
            @RequestParam(defaultValue = "1") int day) {
        try (SqlSession session = databaseService.openSession(dbFile)) {
            int stockIndex;
            try { stockIndex = Integer.parseInt(stockId); } catch (NumberFormatException e) {
                var stockMapper = session.getMapper(jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockMapper.class);
                var entity = stockMapper.selectByCode(stockId);
                stockIndex = entity != null ? entity.getId() : -1;
            }
            TradeRecordMapper mapper = session.getMapper(TradeRecordMapper.class);
            List<TradeRecordEntity> trades = mapper.selectByStockAndDay(stockIndex, day);
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
