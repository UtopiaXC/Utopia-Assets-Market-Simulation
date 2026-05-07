package jp.ac.tsukuba.eclab.assetmarketsimulation.api;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.TraderDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.TradeRecordEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.TradeRecordMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.DatabaseService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.TraderAnalysisService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.service.TraderAnalysisService.TraderSummary;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for trader analysis
 */
@RestController
@RequestMapping("/api/simulations/{dbFile}/traders")
public class TraderController {

    @Autowired
    private TraderAnalysisService traderAnalysisService;

    @Autowired
    private DatabaseService databaseService;

    @GetMapping
    public ResponseEntity<?> getTraderList(
            @PathVariable String dbFile,
            @RequestParam(defaultValue = "1") int day) {
        try {
            List<TraderSummary> traders = traderAnalysisService.getTraderList(dbFile, day);
            return ResponseEntity.ok(traders);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{traderId}")
    public ResponseEntity<?> getTraderDetail(
            @PathVariable String dbFile,
            @PathVariable int traderId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            TraderDetailDTO detail = traderAnalysisService.getTraderDetail(dbFile, traderId, day);
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{traderId}/history")
    public ResponseEntity<?> getTraderHistory(
            @PathVariable String dbFile,
            @PathVariable int traderId) {
        try {
            TraderDetailDTO detail = traderAnalysisService.getTraderDetail(dbFile, traderId, 1);
            return ResponseEntity.ok(Map.of("history", detail.history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{traderId}/holdings")
    public ResponseEntity<?> getTraderHoldings(
            @PathVariable String dbFile,
            @PathVariable int traderId,
            @RequestParam(defaultValue = "1") int day) {
        try {
            TraderDetailDTO detail = traderAnalysisService.getTraderDetail(dbFile, traderId, day);
            return ResponseEntity.ok(detail.holdings);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * NEW: Get trade records for a specific trader on a specific day
     * GET /api/simulations/{dbFile}/traders/{traderId}/trades?day=1
     */
    @GetMapping("/{traderId}/trades")
    public ResponseEntity<?> getTraderTrades(
            @PathVariable String dbFile,
            @PathVariable int traderId,
            @RequestParam(defaultValue = "1") int day) {
        try (SqlSession session = databaseService.openSession(dbFile)) {
            TradeRecordMapper mapper = session.getMapper(TradeRecordMapper.class);
            List<TradeRecordEntity> trades = mapper.selectByAgentAndDay(traderId, day);
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
