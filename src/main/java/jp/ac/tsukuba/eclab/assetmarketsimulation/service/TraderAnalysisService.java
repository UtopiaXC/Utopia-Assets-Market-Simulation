package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.TraderDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.TraderDetailDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentAssetDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.HoldingsSnapshotEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.AgentAssetDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.AgentMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.HoldingsMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockDailyMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Trader analysis service - uses MyBatis mappers
 */
@Service
public class TraderAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public List<TraderSummary> getTraderList(String dbFile, int day) {
        List<TraderSummary> traders = new ArrayList<>();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            AgentAssetDailyMapper mapper = session.getMapper(AgentAssetDailyMapper.class);
            for (AgentAssetDailyEntity e : mapper.selectByDay(day)) {
                traders.add(new TraderSummary(
                        e.getAgentId(),
                        e.getAgentType() != null ? e.getAgentType() : "",
                        e.getIsActive() != null ? e.getIsActive() : false,
                        e.getTotalAssets() != null ? e.getTotalAssets() : 0,
                        e.getPrivateSavings() != null ? e.getPrivateSavings() : 0,
                        e.getCash() != null ? e.getCash() : 0,
                        e.getStockValue() != null ? e.getStockValue() : 0));
            }
        }
        return traders;
    }

    public TraderDetailDTO getTraderDetail(String dbFile, int traderId, int day) {
        TraderDetailDTO result = new TraderDetailDTO();
        result.traderId = traderId;

        try (SqlSession session = databaseService.openSession(dbFile)) {
            AgentMapper agentMapper = session.getMapper(AgentMapper.class);
            AgentAssetDailyMapper assetMapper = session.getMapper(AgentAssetDailyMapper.class);

            // Get static agent info
            AgentEntity agent = agentMapper.selectById(traderId);
            if (agent != null) {
                result.traderType = agent.getAgentType();
            }

            // Get history
            List<AgentAssetDailyEntity> history = assetMapper.selectByAgentId(traderId);
            result.history = new ArrayList<>();
            for (AgentAssetDailyEntity e : history) {
                TraderDayData data = new TraderDayData();
                data.day = e.getDay();
                data.totalAssets = e.getTotalAssets() != null ? e.getTotalAssets() : 0;
                data.privateSavings = e.getPrivateSavings() != null ? e.getPrivateSavings() : 0;
                data.cash = e.getCash() != null ? e.getCash() : 0;
                data.reservedCash = e.getReservedCash() != null ? e.getReservedCash() : 0;
                data.stockValue = e.getStockValue() != null ? e.getStockValue() : 0;
                data.riskTolerance = e.getRiskTolerance() != null ? e.getRiskTolerance() : 0;
                data.isActive = e.getIsActive() != null ? e.getIsActive() : false;
                result.history.add(data);
            }

            // Current day status
            AgentAssetDailyEntity current = assetMapper.selectByAgentIdAndDay(traderId, day);
            if (current != null) {
                result.isActive = current.getIsActive() != null ? current.getIsActive() : false;
            }

            // Get current metrics
            result.currentMetrics = getCurrentMetrics(traderId, day, result.history);

            // Get holdings from snapshot
            result.holdings = getHoldings(session, traderId, day);
        }
        return result;
    }

    private TraderMetrics getCurrentMetrics(int traderId, int day, List<TraderDayData> history) {
        TraderDayData current = null;
        TraderDayData previous = null;
        for (TraderDayData data : history) {
            if (data.day == day) current = data;
            else if (data.day == day - 1) previous = data;
        }
        if (current == null) return null;

        double dailyPnl = previous != null ? current.totalAssets - previous.totalAssets : 0;
        return new TraderMetrics(current.totalAssets, dailyPnl, current.privateSavings,
                current.cash, current.reservedCash, current.stockValue, current.riskTolerance);
    }

    private List<Holding> getHoldings(SqlSession session, int traderId, int day) {
        List<Holding> holdings = new ArrayList<>();
        try {
            HoldingsMapper holdingsMapper = session.getMapper(HoldingsMapper.class);
            StockDailyMapper stockDailyMapper = session.getMapper(StockDailyMapper.class);

            HoldingsSnapshotEntity snapshot = holdingsMapper.selectLatestSnapshot(traderId, day);
            if (snapshot == null) return holdings;

            Map<String, Double> holdingsMap = jsonMapper.readValue(
                    snapshot.getHoldingsJson(), new TypeReference<Map<String, Double>>() {});

            for (Map.Entry<String, Double> entry : holdingsMap.entrySet()) {
                int stockId = Integer.parseInt(entry.getKey());
                double quantity = entry.getValue();

                StockDailyEntity stockDaily = stockDailyMapper.selectByStockIdAndDay(stockId, day);
                double price = stockDaily != null ? stockDaily.getClose() : 0;

                holdings.add(new Holding(
                        stockDaily != null && stockDaily.getStockCode() != null ?
                                stockDaily.getStockCode() : entry.getKey(),
                        quantity, price, quantity * price));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return holdings;
    }

    /**
     * Trader summary DTO for list view
     */
    public static class TraderSummary {
        public int traderId;
        public String traderType;
        public boolean isActive;
        public double totalAssets;
        public double privateSavings;
        public double cash;
        public double stockValue;

        public TraderSummary(int traderId, String traderType, boolean isActive,
                double totalAssets, double privateSavings, double cash, double stockValue) {
            this.traderId = traderId;
            this.traderType = traderType;
            this.isActive = isActive;
            this.totalAssets = totalAssets;
            this.privateSavings = privateSavings;
            this.cash = cash;
            this.stockValue = stockValue;
        }
    }
}
