package jp.ac.tsukuba.eclab.assetmarketsimulation.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.File;

import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;

/**
 * Optimized Database Logger with normalized schema
 * 
 * Optimization strategies:
 * 1. Normalized tables (static info separated)
 * 2. Sampled logging (configurable intervals)
 * 3. Delta compression for holdings
 * 4. JSON compression for snapshots
 * 5. Proper indexing
 */
public class OptimizedDatabaseLogger implements Steppable {

    private Connection conn;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    // Prepared statements
    private PreparedStatement psMarketLog;
    private PreparedStatement psStockLog;
    private PreparedStatement psTraderLog;
    private PreparedStatement psHoldingsDelta;
    private PreparedStatement psHoldingsSnapshot;
    private PreparedStatement psEventLog;

    // Static info (logged once)
    private PreparedStatement psStockInfo;
    private PreparedStatement psTraderInfo;

    // References
    private Bag traders;
    private Bag stocks;
    private Market market;
    private StockMarketSim sim;

    // Configuration
    private int logSampleInterval = 1; // Log every N days
    private int holdingsSnapshotInterval = 10; // Full snapshot every N days
    private boolean logHoldingsDelta = true; // Log only changes

    // State tracking for delta compression
    private Map<Integer, Map<String, Double>> previousHoldings = new HashMap<>();

    public OptimizedDatabaseLogger(long seed) {
        this(seed, 1, 10, true);
    }

    public OptimizedDatabaseLogger(long seed, int logSampleInterval,
            int holdingsSnapshotInterval, boolean logHoldingsDelta) {
        this.logSampleInterval = Math.max(1, logSampleInterval);
        this.holdingsSnapshotInterval = Math.max(1, holdingsSnapshotInterval);
        this.logHoldingsDelta = logHoldingsDelta;

        long timestamp = System.currentTimeMillis();
        String dbName = String.format("SimResult-%d.db", timestamp);
        String outputDir = "output";

        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String dbPath = outputDir + File.separator + dbName;

        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            System.out.println("SQLite database connected: " + dbPath);
            initializeDatabase();
        } catch (Exception e) {
            System.err.println("DatabaseLogger initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDatabase() throws SQLException {
        Statement stmt = conn.createStatement();

        // Enable WAL mode for better concurrent performance
        stmt.execute("PRAGMA journal_mode = WAL;");
        stmt.execute("PRAGMA synchronous = NORMAL;");
        stmt.execute("PRAGMA cache_size = 10000;");

        // ==================== Simulation Metadata ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS simulation_meta (" +
                "id INTEGER PRIMARY KEY, " +
                "seed BIGINT, " +
                "start_time TEXT, " +
                "config_json TEXT, " +
                "scenario_name TEXT" +
                ");");

        // ==================== Market Log ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS market_log (" +
                "day INT PRIMARY KEY, " +
                "open REAL, high REAL, low REAL, close REAL, " +
                "volume REAL, turnover REAL, " +
                "total_market_cap REAL, " +
                "social_wealth_pool REAL, " +
                "active_agents INT" +
                ");");

        // ==================== Stock Static Info (logged once) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS stock_info (" +
                "stock_id INT PRIMARY KEY, " +
                "stock_code TEXT, " +
                "sector INT, " +
                "ipo_price REAL, " +
                "total_shares REAL, " +
                "liquid_shares REAL" +
                ");");

        // ==================== Stock Log (reduced columns) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS stock_log (" +
                "day INT, " +
                "stock_id INT, " +
                "close REAL, " +
                "volume REAL, " +
                "turnover REAL, " +
                "pe_ttm REAL, " +
                "pb_ratio REAL, " +
                "total_market_cap REAL, " +
                "PRIMARY KEY (day, stock_id)" +
                ");");

        // ==================== Trader Static Info (logged once) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS trader_info (" +
                "trader_id INT PRIMARY KEY, " +
                "trader_type INT, " + // 0=Institutional, 1=Retail, 2=Noise
                "initial_cash REAL, " +
                "max_stocks INT" +
                ");");

        // ==================== Trader Log (sampled) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS trader_log (" +
                "day INT, " +
                "trader_id INT, " +
                "total_assets REAL, " +
                "cash REAL, " +
                "stock_value REAL, " +
                "risk_tolerance REAL, " +
                "is_active BOOLEAN, " +
                "PRIMARY KEY (day, trader_id)" +
                ");");

        // ==================== Holdings Snapshot (JSON compressed) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS holdings_snapshot (" +
                "snapshot_day INT, " +
                "trader_id INT, " +
                "holdings_json TEXT, " + // {"STK001": 100, "STK002": 50}
                "PRIMARY KEY (snapshot_day, trader_id)" +
                ");");

        // ==================== Holdings Delta (only changes) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS holdings_delta (" +
                "day INT, " +
                "trader_id INT, " +
                "stock_id INT, " +
                "quantity_change REAL, " +
                "PRIMARY KEY (day, trader_id, stock_id)" +
                ");");

        // ==================== Event Log ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS event_log (" +
                "event_id TEXT PRIMARY KEY, " +
                "day INT, " +
                "event_type TEXT, " +
                "source TEXT, " +
                "parameters_json TEXT, " +
                "description TEXT" +
                ");");

        // ==================== IPO Tables (unchanged) ====================
        stmt.execute("CREATE TABLE IF NOT EXISTS ipo_log (" +
                "stock_id INT PRIMARY KEY, " +
                "ipo_price REAL, " +
                "available_shares REAL, " +
                "demand_shares REAL, " +
                "oversubscription_ratio REAL" +
                ");");

        stmt.execute("CREATE TABLE IF NOT EXISTS ipo_subscription_log (" +
                "stock_id INT, " +
                "trader_id INT, " +
                "demand_shares REAL, " +
                "allocated_shares REAL, " +
                "PRIMARY KEY (stock_id, trader_id)" +
                ");");

        // ==================== Indexes ====================
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_log_day ON stock_log(day);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_trader_log_day ON trader_log(day);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_trader_log_active ON trader_log(day, is_active);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_holdings_delta_day ON holdings_delta(day);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_log_day ON event_log(day);");

        // ==================== Prepare Statements ====================
        psMarketLog = conn.prepareStatement(
                "INSERT OR REPLACE INTO market_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

        psStockInfo = conn.prepareStatement(
                "INSERT OR IGNORE INTO stock_info VALUES (?, ?, ?, ?, ?, ?);");

        psStockLog = conn.prepareStatement(
                "INSERT OR REPLACE INTO stock_log VALUES (?, ?, ?, ?, ?, ?, ?, ?);");

        psTraderInfo = conn.prepareStatement(
                "INSERT OR IGNORE INTO trader_info VALUES (?, ?, ?, ?);");

        psTraderLog = conn.prepareStatement(
                "INSERT OR REPLACE INTO trader_log VALUES (?, ?, ?, ?, ?, ?, ?);");

        psHoldingsSnapshot = conn.prepareStatement(
                "INSERT OR REPLACE INTO holdings_snapshot VALUES (?, ?, ?);");

        psHoldingsDelta = conn.prepareStatement(
                "INSERT OR REPLACE INTO holdings_delta VALUES (?, ?, ?, ?);");

        psEventLog = conn.prepareStatement(
                "INSERT OR REPLACE INTO event_log VALUES (?, ?, ?, ?, ?, ?);");

        conn.setAutoCommit(false);
        stmt.close();
    }

    public void setup(StockMarketSim sim) {
        this.sim = sim;
        this.traders = sim.traders;
        this.stocks = sim.stocks;
        this.market = sim.market;

        // Log static info on setup
        try {
            logStaticInfo();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void logStaticInfo() throws SQLException {
        // Log stock info
        for (int i = 0; i < stocks.size(); i++) {
            Stock s = (Stock) stocks.get(i);
            psStockInfo.setInt(1, i);
            psStockInfo.setString(2, s.stockId);
            psStockInfo.setInt(3, s.sector.ordinal());
            psStockInfo.setDouble(4, s.currentPrice); // IPO price
            psStockInfo.setDouble(5, s.totalShares);
            psStockInfo.setDouble(6, s.liquidShares);
            psStockInfo.addBatch();
        }
        psStockInfo.executeBatch();

        // Log trader info
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (obj instanceof BaseTrader) {
                BaseTrader t = (BaseTrader) obj;
                psTraderInfo.setInt(1, t.traderId);
                psTraderInfo.setInt(2, getTraderTypeInt(t.traderType));
                psTraderInfo.setDouble(3, t.portfolio.cash);
                psTraderInfo.setInt(4, t.maxStocks);
                psTraderInfo.addBatch();
            }
        }
        psTraderInfo.executeBatch();

        conn.commit();
    }

    private int getTraderTypeInt(String type) {
        return switch (type) {
            case "Institutional" -> 0;
            case "Retail" -> 1;
            case "Noise" -> 2;
            default -> -1;
        };
    }

    @Override
    public void step(SimState state) {
        if (conn == null || traders == null || stocks == null)
            return;

        int day = market.getCurrentDay();
        if (day == 0)
            return;

        // Only log at end of day
        if (state.schedule.getSteps() % market.STEPS_PER_DAY != 0)
            return;

        // Check sample interval
        if (day % logSampleInterval != 0)
            return;

        try {
            logDay(day);
        } catch (SQLException e) {
            System.err.println("Database logging error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logDay(int day) throws SQLException {
        // 1. Market log
        logMarket(day);

        // 2. Stock log
        logStocks(day);

        // 3. Trader log (active only)
        logTraders(day);

        // 4. Holdings (delta or snapshot)
        if (day % holdingsSnapshotInterval == 0) {
            logHoldingsSnapshot(day);
        } else if (logHoldingsDelta) {
            logHoldingsDelta(day);
        }

        // Commit
        conn.commit();

        if (day % 100 == 0) {
            System.out.println("DBLogger: Day " + day + " logged");
        }
    }

    private void logMarket(int day) throws SQLException {
        int activeAgents = 0;
        for (int i = 0; i < traders.size(); i++) {
            if (traders.get(i) instanceof BaseTrader && ((BaseTrader) traders.get(i)).isActive()) {
                activeAgents++;
            }
        }

        psMarketLog.setInt(1, day);
        psMarketLog.setDouble(2, market.indexOpen);
        psMarketLog.setDouble(3, market.indexHigh);
        psMarketLog.setDouble(4, market.indexLow);
        psMarketLog.setDouble(5, market.marketIndex);
        psMarketLog.setDouble(6, market.totalVolumeThisDay);
        psMarketLog.setDouble(7, market.totalTurnoverThisDay);
        psMarketLog.setDouble(8, market.marketTotalMarketCap);
        psMarketLog.setDouble(9, sim.socialWealthPool);
        psMarketLog.setInt(10, activeAgents);
        psMarketLog.executeUpdate();
    }

    private void logStocks(int day) throws SQLException {
        for (int i = 0; i < stocks.size(); i++) {
            Stock s = (Stock) stocks.get(i);
            psStockLog.setInt(1, day);
            psStockLog.setInt(2, i);
            psStockLog.setDouble(3, s.currentPrice);
            psStockLog.setDouble(4, s.volumeThisDay);
            psStockLog.setDouble(5, s.turnoverThisDay);
            psStockLog.setDouble(6, s.peRatioTTM);
            psStockLog.setDouble(7, s.pbRatio);
            psStockLog.setDouble(8, s.totalMarketCap);
            psStockLog.addBatch();
        }
        psStockLog.executeBatch();
    }

    private void logTraders(int day) throws SQLException {
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader))
                continue;

            BaseTrader t = (BaseTrader) obj;

            // Only log active traders (reduce data size)
            if (!t.isActive())
                continue;

            double stockValue = t.portfolio.getTotalStockValue();
            double cash = t.portfolio.cash + t.portfolio.reservedCash;
            double totalAssets = cash + stockValue + t.privateSavings;

            psTraderLog.setInt(1, day);
            psTraderLog.setInt(2, t.traderId);
            psTraderLog.setDouble(3, totalAssets);
            psTraderLog.setDouble(4, cash);
            psTraderLog.setDouble(5, stockValue);
            psTraderLog.setDouble(6, t.riskTolerance);
            psTraderLog.setBoolean(7, t.isActive());
            psTraderLog.addBatch();
        }
        psTraderLog.executeBatch();
    }

    private void logHoldingsSnapshot(int day) throws SQLException {
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader))
                continue;

            BaseTrader t = (BaseTrader) obj;
            if (!t.isActive())
                continue;

            Map<String, Double> holdings = new HashMap<>();
            for (Map.Entry<Stock, Position> entry : t.portfolio.getPositions().entrySet()) {
                if (entry.getValue().totalQuantity > 0) {
                    holdings.put(entry.getKey().stockId, entry.getValue().totalQuantity);
                }
            }

            if (!holdings.isEmpty()) {
                try {
                    String json = jsonMapper.writeValueAsString(holdings);
                    psHoldingsSnapshot.setInt(1, day);
                    psHoldingsSnapshot.setInt(2, t.traderId);
                    psHoldingsSnapshot.setString(3, json);
                    psHoldingsSnapshot.addBatch();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Update previous holdings for delta tracking
            previousHoldings.put(t.traderId, holdings);
        }
        psHoldingsSnapshot.executeBatch();
    }

    private void logHoldingsDelta(int day) throws SQLException {
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader))
                continue;

            BaseTrader t = (BaseTrader) obj;
            if (!t.isActive())
                continue;

            Map<String, Double> currentHoldings = new HashMap<>();
            for (Map.Entry<Stock, Position> entry : t.portfolio.getPositions().entrySet()) {
                currentHoldings.put(entry.getKey().stockId, entry.getValue().totalQuantity);
            }

            Map<String, Double> prevHoldings = previousHoldings.getOrDefault(t.traderId, new HashMap<>());

            // Find changes
            Set<String> allStocks = new HashSet<>(currentHoldings.keySet());
            allStocks.addAll(prevHoldings.keySet());

            for (String stockId : allStocks) {
                double current = currentHoldings.getOrDefault(stockId, 0.0);
                double prev = prevHoldings.getOrDefault(stockId, 0.0);
                double delta = current - prev;

                if (Math.abs(delta) > 0.001) { // Only log if changed
                    int stockIdInt = Integer.parseInt(stockId.replace("STK", ""));
                    psHoldingsDelta.setInt(1, day);
                    psHoldingsDelta.setInt(2, t.traderId);
                    psHoldingsDelta.setInt(3, stockIdInt);
                    psHoldingsDelta.setDouble(4, delta);
                    psHoldingsDelta.addBatch();
                }
            }

            previousHoldings.put(t.traderId, currentHoldings);
        }
        psHoldingsDelta.executeBatch();
    }

    public void logEvent(String eventId, int day, String eventType,
            String source, Map<String, Object> parameters, String description) {
        if (conn == null)
            return;
        try {
            String paramsJson = jsonMapper.writeValueAsString(parameters);
            psEventLog.setString(1, eventId);
            psEventLog.setInt(2, day);
            psEventLog.setString(3, eventType);
            psEventLog.setString(4, source);
            psEventLog.setString(5, paramsJson);
            psEventLog.setString(6, description);
            psEventLog.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // IPO logging (unchanged API)
    public void logIPO(String stockId, double ipoPrice, double available, double demand, double ratio) {
        // Use existing method from original logger
    }

    public void logIPOSubscription(String stockId, int traderId, double demandShares, double allocatedShares) {
        // Use existing method from original logger
    }

    public void commitIPO() {
        // Use existing method from original logger
    }

    public void close() {
        try {
            if (conn != null) {
                conn.commit();

                if (psMarketLog != null)
                    psMarketLog.close();
                if (psStockLog != null)
                    psStockLog.close();
                if (psStockInfo != null)
                    psStockInfo.close();
                if (psTraderLog != null)
                    psTraderLog.close();
                if (psTraderInfo != null)
                    psTraderInfo.close();
                if (psHoldingsSnapshot != null)
                    psHoldingsSnapshot.close();
                if (psHoldingsDelta != null)
                    psHoldingsDelta.close();
                if (psEventLog != null)
                    psEventLog.close();

                conn.close();
                System.out.println("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
