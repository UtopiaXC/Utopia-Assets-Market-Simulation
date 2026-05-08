package jp.ac.tsukuba.eclab.assetmarketsimulation.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model.ValuationService;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Simulation data logger using MyBatis DAO layer.
 *
 * Features:
 * - 3NF normalized schema (static info logged once, daily data separately)
 * - Trade records for all market matches
 * - Delta + periodic snapshot strategy for holdings
 * - Social influence logging for network visualization
 * - Leverage/margin tracking
 * - Extensible event logging with JSON parameters
 */
public class SimulationDataLogger implements Steppable {

    private SqlSessionFactory sessionFactory;
    private SqlSession batchSession; // Long-lived session for batch writes

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private Bag traders;
    private Bag stocks;
    private Market market;
    private StockMarketSim sim;

    // Configuration
    private static final int HOLDINGS_SNAPSHOT_INTERVAL = 50; // Full snapshot every N days

    // State tracking for delta compression
    private Map<Integer, Map<String, Double>> previousHoldings = new HashMap<>();

    // Trade record buffer (filled by Market during matching)
    private final List<TradeRecordEntity> tradeBuffer = Collections.synchronizedList(new ArrayList<>());



    // Event buffer
    private final List<String[]> eventBuffer = Collections.synchronizedList(new ArrayList<>());

    private String dbPath;

    public SimulationDataLogger(long seed) {
        this(seed, null);
    }

    public SimulationDataLogger(long seed, String customDbName) {
        long timestamp = System.currentTimeMillis();
        String dbName = customDbName;
        if (dbName == null || dbName.trim().isEmpty()) {
            dbName = String.format("SimulationResult-%d.db", timestamp);
        }
        if (!dbName.endsWith(".db")) {
            dbName = dbName + ".db";
        }

        String outputDir = "output";
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.dbPath = outputDir + File.separator + dbName;

        try {
            sessionFactory = DynamicSqlSessionManager.createNew(dbPath);
            DynamicSqlSessionManager.initializeSchema(sessionFactory);
            batchSession = sessionFactory.openSession(false); // manual commit
            System.out.println("SimulationDataLogger initialized: " + dbPath);
        } catch (Exception e) {
            System.err.println("SimulationDataLogger initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setup(StockMarketSim sim) {
        this.sim = sim;
        this.traders = sim.traders;
        this.stocks = sim.stocks;
        this.market = sim.market;

        try {
            logStaticData();
        } catch (Exception e) {
            System.err.println("Failed to log static data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Log all static/immutable data at simulation start.
     * - Simulation metadata
     * - Sectors
     * - Stocks (static attributes only)
     * - Agents (static attributes only)
     */
    private void logStaticData() {
        // 1. Simulation metadata
        SimulationMetaMapper metaMapper = batchSession.getMapper(SimulationMetaMapper.class);
        SimulationMeta meta = new SimulationMeta();
        meta.setSeed(sim.seed());
        meta.setStartTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.setScenarioName("default");
        meta.setNumStocks(stocks.size());
        meta.setNumAgents(traders.size());
        meta.setSimulationDays(sim.simulationDays);
        meta.setStepsPerDay(market.STEPS_PER_DAY);
        metaMapper.insert(meta);

        // 2. Sectors
        SectorMapper sectorMapper = batchSession.getMapper(SectorMapper.class);
        for (Sector s : Sector.values()) {
            SectorEntity entity = new SectorEntity(s.ordinal(), s.name(), s.name());
            sectorMapper.insert(entity);
        }

        // 3. Stocks
        StockMapper stockMapper = batchSession.getMapper(StockMapper.class);
        for (int i = 0; i < stocks.size(); i++) {
            Stock s = (Stock) stocks.get(i);
            StockEntity entity = new StockEntity();
            entity.setId(i);
            entity.setStockCode(s.stockId);
            entity.setSectorId(s.sector.ordinal());
            entity.setIpoPrice(s.ipoPrice);
            entity.setTotalShares(s.totalShares);
            entity.setLiquidShares(s.liquidShares);
            entity.setInitialNetAssets(s.netAssetsPerShare);
            entity.setInitialEps(s.eps);
            entity.setEarningsGrowth(s.earningsGrowth);
            entity.setBeta(s.beta);
            stockMapper.insert(entity);
        }

        // 4. Agents
        AgentMapper agentMapper = batchSession.getMapper(AgentMapper.class);
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (obj instanceof BaseTrader t) {
                AgentEntity entity = new AgentEntity();
                entity.setId(t.traderId);
                entity.setAgentType(t.traderType);
                entity.setInitialCash(t.portfolio.cash + t.portfolio.reservedCash);
                entity.setMaxStocks(t.maxStocks);
                entity.setInitialRiskTolerance(t.riskTolerance);
                agentMapper.insert(entity);
            }
        }

        batchSession.commit();
        System.out.println("Static data logged: " + stocks.size() + " stocks, " + traders.size() + " agents");
    }

    /**
     * Called by Market when a trade is matched.
     * Buffered for batch insert at end of day.
     * @param influenceJson optional JSON string with social influence data for this trade
     */
    public void logTrade(int day, int stockIndex, int buyerId, int sellerId,
                         double price, double quantity, String influenceJson) {
        TradeRecordEntity record = new TradeRecordEntity();
        record.setDay(day);
        record.setStockId(stockIndex);
        record.setBuyerId(buyerId);
        record.setSellerId(sellerId);
        record.setPrice(price);
        record.setQuantity(quantity);
        record.setInfluenceJson(influenceJson);
        tradeBuffer.add(record);
    }

    /**
     * Convenience overload without influence data.
     */
    public void logTrade(int day, int stockIndex, int buyerId, int sellerId,
                         double price, double quantity) {
        logTrade(day, stockIndex, buyerId, sellerId, price, quantity, null);
    }



    /**
     * Log an event (circuit breaker, margin call, etc.)
     */
    public void logEvent(String eventType, int day, String description) {
        eventBuffer.add(new String[]{eventType, String.valueOf(day), description});
    }

    /**
     * Log a new agent added during simulation (dynamic agent creation).
     */
    public void logNewAgent(BaseTrader t) {
        if (batchSession == null) return;
        try {
            AgentMapper mapper = batchSession.getMapper(AgentMapper.class);
            AgentEntity entity = new AgentEntity();
            entity.setId(t.traderId);
            entity.setAgentType(t.traderType);
            entity.setInitialCash(t.portfolio.cash);
            entity.setMaxStocks(t.maxStocks);
            entity.setInitialRiskTolerance(t.riskTolerance);
            mapper.insert(entity);
            // Don't commit here - will be committed with daily batch
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void step(SimState state) {
        if (batchSession == null || traders == null || stocks == null) return;

        int day = market.getCurrentDay();
        if (day == 0) return;

        try {
            logDailyData(day);
        } catch (Exception e) {
            System.err.println("CRITICAL LOGGER ERROR during step(): " + e.getMessage());
            e.printStackTrace();
            try {
                batchSession.rollback();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void logDailyData(int day) {
        // 1. Market daily
        logMarketDaily(day);

        // 2. Stock daily
        logStockDaily(day);

        // 3. Agent asset daily (with leverage data)
        logAgentAssetDaily(day);

        // 4. Flush trade buffer
        flushTradeBuffer();



        // 6. Flush event buffer
        flushEventBuffer();

        // 7. Holdings (delta + periodic snapshot)
        boolean isSnapshotDay = (day % HOLDINGS_SNAPSHOT_INTERVAL == 0);
        if (isSnapshotDay) {
            logHoldingsSnapshot(day);
        } else {
            logHoldingsDelta(day);
        }

        // Commit
        batchSession.commit();

        if (day % 100 == 0) {
            System.out.println("DBLogger: Day " + day + " logged");
        }
    }

    private void logMarketDaily(int day) {
        MarketDailyMapper mapper = batchSession.getMapper(MarketDailyMapper.class);
        MarketDailyEntity entity = new MarketDailyEntity();
        entity.setDay(day);
        entity.setOpen(market.indexOpen);
        entity.setHigh(market.indexHigh);
        entity.setLow(market.indexLow);
        entity.setClose(market.marketIndex);
        entity.setVolume(market.totalVolumeThisDay);
        entity.setTurnover(market.totalTurnoverThisDay);
        entity.setTotalMarketCap(market.marketTotalMarketCap);
        entity.setAmplitude(market.marketAmplitude);
        entity.setTurnoverRate(market.marketTurnoverRate);
        entity.setSocialWealthPool(sim.socialWealthPool);

        int activeAgents = 0;
        for (int i = 0; i < traders.size(); i++) {
            if (traders.get(i) instanceof BaseTrader bt && bt.isActive()) {
                activeAgents++;
            }
        }
        entity.setActiveAgents(activeAgents);
        mapper.insert(entity);
    }

    private void logStockDaily(int day) {
        StockDailyMapper mapper = batchSession.getMapper(StockDailyMapper.class);
        for (int i = 0; i < stocks.size(); i++) {
            Stock s = (Stock) stocks.get(i);
            StockDailyEntity entity = new StockDailyEntity();
            entity.setDay(day);
            entity.setStockId(i);
            entity.setOpen(s.open);
            entity.setHigh(s.high);
            entity.setLow(s.low);
            entity.setClose(s.currentPrice);
            entity.setVolume(s.volumeThisDay);
            entity.setTurnover(s.turnoverThisDay);
            entity.setPbRatio(s.pbRatio);
            entity.setPeTtm(s.peRatioTTM);
            entity.setPeDynamic(s.peDynamic);
            entity.setPeStatic(s.peStatic);
            entity.setEps(s.eps);
            entity.setNetAssets(s.netAssetsPerShare);
            entity.setTotalMarketCap(s.totalMarketCap);
            entity.setLiquidMarketCap(s.liquidMarketCap);
            entity.setTurnoverRate(s.turnoverRate);
            entity.setAmplitude(s.amplitude);
            entity.setHigh52w(s.high52w);
            entity.setLow52w(s.low52w);
            mapper.insert(entity);
        }
    }

    private void logAgentAssetDaily(int day) {
        AgentAssetDailyMapper mapper = batchSession.getMapper(AgentAssetDailyMapper.class);
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader t)) continue;

            AgentAssetDailyEntity entity = new AgentAssetDailyEntity();
            entity.setDay(day);
            entity.setAgentId(t.traderId);
            entity.setCash(t.portfolio.cash);
            entity.setReservedCash(t.portfolio.reservedCash);
            entity.setPrivateSavings(t.privateSavings);
            double stockValue = t.portfolio.getTotalStockValue();
            entity.setStockValue(stockValue);
            entity.setTotalAssets(t.portfolio.cash + t.portfolio.reservedCash + stockValue + t.privateSavings);
            entity.setRiskTolerance(t.riskTolerance);
            entity.setIsActive(t.isActive());
            mapper.insert(entity);
        }
    }

    private void flushTradeBuffer() {
        if (tradeBuffer.isEmpty()) return;
        TradeRecordMapper mapper = batchSession.getMapper(TradeRecordMapper.class);
        List<TradeRecordEntity> records;
        synchronized (tradeBuffer) {
            records = new ArrayList<>(tradeBuffer);
            tradeBuffer.clear();
        }
        for (TradeRecordEntity record : records) {
            mapper.insert(record);
        }
    }



    private void flushEventBuffer() {
        if (eventBuffer.isEmpty()) return;
        EventLogMapper mapper = batchSession.getMapper(EventLogMapper.class);
        List<String[]> events;
        synchronized (eventBuffer) {
            events = new ArrayList<>(eventBuffer);
            eventBuffer.clear();
        }
        for (String[] event : events) {
            EventLogEntity entity = new EventLogEntity();
            entity.setEventId(UUID.randomUUID().toString().substring(0, 8));
            entity.setEventType(event[0]);
            entity.setDay(Integer.parseInt(event[1]));
            entity.setDescription(event[2]);
            entity.setSource("SYSTEM");
            mapper.insert(entity);
        }
    }

    private void logHoldingsSnapshot(int day) {
        HoldingsMapper mapper = batchSession.getMapper(HoldingsMapper.class);
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader t)) continue;
            if (!t.isActive()) continue;

            Map<String, Double> holdings = new HashMap<>();
            for (Map.Entry<Stock, Position> entry : t.portfolio.getPositions().entrySet()) {
                if (entry.getValue().totalQuantity > 0) {
                    // Use stock index as key
                    int stockIdx = findStockIndex(entry.getKey());
                    holdings.put(String.valueOf(stockIdx), entry.getValue().totalQuantity);
                }
            }

            if (!holdings.isEmpty()) {
                try {
                    HoldingsSnapshotEntity snapshot = new HoldingsSnapshotEntity();
                    snapshot.setSnapshotDay(day);
                    snapshot.setAgentId(t.traderId);
                    snapshot.setHoldingsJson(jsonMapper.writeValueAsString(holdings));
                    mapper.insertSnapshot(snapshot);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            previousHoldings.put(t.traderId, holdings);
        }
    }

    private void logHoldingsDelta(int day) {
        HoldingsMapper mapper = batchSession.getMapper(HoldingsMapper.class);
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader t)) continue;
            if (!t.isActive()) continue;

            Map<String, Double> currentHoldings = new HashMap<>();
            for (Map.Entry<Stock, Position> entry : t.portfolio.getPositions().entrySet()) {
                int stockIdx = findStockIndex(entry.getKey());
                currentHoldings.put(String.valueOf(stockIdx), entry.getValue().totalQuantity);
            }

            Map<String, Double> prevHoldings = previousHoldings.getOrDefault(t.traderId, new HashMap<>());

            Set<String> allStockKeys = new HashSet<>(currentHoldings.keySet());
            allStockKeys.addAll(prevHoldings.keySet());

            for (String stockKey : allStockKeys) {
                double current = currentHoldings.getOrDefault(stockKey, 0.0);
                double prev = prevHoldings.getOrDefault(stockKey, 0.0);
                double delta = current - prev;

                if (Math.abs(delta) > 0.001) {
                    HoldingsDeltaEntity deltaEntity = new HoldingsDeltaEntity();
                    deltaEntity.setDay(day);
                    deltaEntity.setAgentId(t.traderId);
                    deltaEntity.setStockId(Integer.parseInt(stockKey));
                    deltaEntity.setQuantityChange(delta);
                    mapper.insertDelta(deltaEntity);
                }
            }
            previousHoldings.put(t.traderId, currentHoldings);
        }
    }

    private int findStockIndex(Stock stock) {
        for (int i = 0; i < stocks.size(); i++) {
            if (stocks.get(i) == stock) return i;
        }
        return -1;
    }

    public void close() {
        try {
            if (batchSession != null) {
                batchSession.commit();
                batchSession.close();
                batchSession = null;
                System.out.println("SimulationDataLogger closed: " + dbPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
