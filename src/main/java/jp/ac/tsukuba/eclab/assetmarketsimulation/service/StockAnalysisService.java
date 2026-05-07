package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StockDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StockDetailDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.HoldingsSnapshotEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.HoldingsMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.AgentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stock analysis service - uses MyBatis mappers
 */
@Service
public class StockAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public List<StockSummary> getStockList(String dbFile, int day) {
        List<StockSummary> stocks = new ArrayList<>();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            StockDailyMapper mapper = session.getMapper(StockDailyMapper.class);
            for (StockDailyEntity e : mapper.selectByDay(day)) {
                stocks.add(new StockSummary(
                        e.getStockCode() != null ? e.getStockCode() : String.valueOf(e.getStockId()),
                        e.getSectorName() != null ? e.getSectorName() : "",
                        e.getClose(), e.getPeTtm(), e.getTotalMarketCap()));
            }
        }
        return stocks;
    }

    public StockDetailDTO getStockDetail(String dbFile, String stockId, int day) {
        StockDetailDTO result = new StockDetailDTO();
        result.stockId = stockId;

        try (SqlSession session = databaseService.openSession(dbFile)) {
            StockMapper stockMapper = session.getMapper(StockMapper.class);
            StockDailyMapper dailyMapper = session.getMapper(StockDailyMapper.class);
            HoldingsMapper holdingsMapper = session.getMapper(HoldingsMapper.class);

            // Resolve stock ID (could be code like "UTEC000001" or numeric index)
            int stockIndex = resolveStockIndex(session, stockId);
            if (stockIndex < 0) return result;

            // Get static stock info
            StockEntity stockEntity = stockMapper.selectById(stockIndex);
            if (stockEntity != null) {
                result.sector = stockEntity.getSectorName();
            }

            // Get history
            List<StockDailyEntity> history = dailyMapper.selectByStockId(stockIndex);
            result.history = new ArrayList<>();
            for (StockDailyEntity e : history) {
                StockDayData data = new StockDayData();
                data.day = e.getDay();
                data.open = e.getOpen();
                data.high = e.getHigh();
                data.low = e.getLow();
                data.close = e.getClose();
                data.volume = e.getVolume();
                data.turnover = e.getTurnover();
                data.pbRatio = e.getPbRatio();
                data.peTtm = e.getPeTtm();
                data.peStatic = e.getPeStatic() != null ? e.getPeStatic() : 0;
                data.peDynamic = e.getPeDynamic() != null ? e.getPeDynamic() : 0;
                data.eps = e.getEps();
                data.netAssets = e.getNetAssets();
                data.totalMarketCap = e.getTotalMarketCap();
                data.liquidMarketCap = e.getLiquidMarketCap();
                data.turnoverRate = e.getTurnoverRate();
                data.amplitude = e.getAmplitude();
                // totalShares/liquidShares come from stock entity now
                data.totalShares = stockEntity != null ? stockEntity.getTotalShares() : 0;
                data.liquidShares = stockEntity != null ? stockEntity.getLiquidShares() : 0;
                data.high52w = e.getHigh52w();
                data.low52w = e.getLow52w();
                result.history.add(data);
            }

            // Get current metrics
            StockDailyEntity current = dailyMapper.selectByStockIdAndDay(stockIndex, day);
            if (current != null) {
                result.currentMetrics = new StockMetrics(
                        current.getClose(), current.getPeTtm(), current.getPbRatio(),
                        current.getTotalMarketCap(), current.getVolume(), current.getTurnoverRate());
            }

            // Get shareholders from holdings snapshots
            result.shareholders = getShareholdersFromSnapshot(session, stockIndex, day);
        }
        return result;
    }

    private int resolveStockIndex(SqlSession session, String stockId) {
        // Try as numeric index first
        try {
            return Integer.parseInt(stockId);
        } catch (NumberFormatException e) {
            // Try as stock code
            StockMapper mapper = session.getMapper(StockMapper.class);
            StockEntity entity = mapper.selectByCode(stockId);
            return entity != null ? entity.getId() : -1;
        }
    }

    private List<Shareholder> getShareholdersFromSnapshot(SqlSession session, int stockIndex, int day) {
        List<Shareholder> shareholders = new ArrayList<>();
        try {
            HoldingsMapper holdingsMapper = session.getMapper(HoldingsMapper.class);
            AgentMapper agentMapper = session.getMapper(AgentMapper.class);

            // Get all snapshots near the requested day
            List<HoldingsSnapshotEntity> snapshots = holdingsMapper.selectSnapshotsByDay(
                    day - (day % 50 == 0 ? 0 : day % 50) // Find closest snapshot day
            );

            String stockKey = String.valueOf(stockIndex);
            for (HoldingsSnapshotEntity snap : snapshots) {
                Map<String, Double> holdings = jsonMapper.readValue(
                        snap.getHoldingsJson(), new TypeReference<Map<String, Double>>() {});
                Double quantity = holdings.get(stockKey);
                if (quantity != null && quantity > 0) {
                    var agent = agentMapper.selectById(snap.getAgentId());
                    shareholders.add(new Shareholder(
                            snap.getAgentId(),
                            agent != null ? agent.getAgentType() : "Unknown",
                            quantity, 0)); // value requires current price, set to 0 for now
                }
            }

            // Sort by quantity descending, limit to 50
            shareholders.sort((a, b) -> Double.compare(b.quantity, a.quantity));
            if (shareholders.size() > 50) {
                shareholders = shareholders.subList(0, 50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return shareholders;
    }

    /**
     * Stock summary DTO for list view
     */
    public static class StockSummary {
        public String stockId;
        public String sector;
        public double close;
        public double peTtm;
        public double totalMarketCap;

        public StockSummary(String stockId, String sector, double close, double peTtm, double totalMarketCap) {
            this.stockId = stockId;
            this.sector = sector;
            this.close = close;
            this.peTtm = peTtm;
            this.totalMarketCap = totalMarketCap;
        }
    }
}
