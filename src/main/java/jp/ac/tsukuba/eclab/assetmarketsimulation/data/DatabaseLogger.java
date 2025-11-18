package jp.ac.tsukuba.eclab.assetmarketsimulation.data;

// (Imports)
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.File;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;


public class DatabaseLogger implements Steppable {

    private Connection conn;
    private PreparedStatement psTraderLog;
    private PreparedStatement psStockLog;
    private PreparedStatement psMarketLog;
    private PreparedStatement psHoldingsLog;

    // (V4.26)
    private PreparedStatement psIPOLog;
    private PreparedStatement psIPOSubscriptionLog;

    private Bag traders;
    private Bag stocks;
    private Market market;

    public DatabaseLogger(long seed) {
        long timestamp = System.currentTimeMillis();
        String dbName = String.format("SimulationResult-%d.db", timestamp);
        String outputDir = "output";
        try { Files.createDirectories(Paths.get(outputDir)); }
        catch (IOException e) { e.printStackTrace(); }
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

        // (V4.20.1 保持不变)
        stmt.execute("DROP TABLE IF EXISTS market_log;");
        stmt.execute("CREATE TABLE market_log (day INT PRIMARY KEY, open REAL, high REAL, low REAL, close REAL, volume REAL, turnover REAL, total_market_cap REAL, amplitude REAL, turnover_rate REAL);");
        stmt.execute("DROP TABLE IF EXISTS stock_log;");
        stmt.execute("CREATE TABLE stock_log (day INT, stock_id VARCHAR(10), sector TEXT, open REAL, high REAL, low REAL, close REAL, volume REAL, turnover REAL, pb_ratio REAL, pe_ttm REAL, eps REAL, net_assets REAL, total_market_cap REAL, liquid_market_cap REAL, turnover_rate REAL, amplitude REAL, pe_dynamic REAL, total_shares REAL, liquid_shares REAL, high_52w REAL, low_52w REAL, pe_static REAL, PRIMARY KEY (day, stock_id));");
        stmt.execute("DROP TABLE IF EXISTS holdings_log;");
        stmt.execute("CREATE TABLE holdings_log (day INT, trader_id INT, stock_id VARCHAR(10), quantity REAL, PRIMARY KEY (day, trader_id, stock_id));");

        // (V4.26 保持不变)
        stmt.execute("DROP TABLE IF EXISTS ipo_log;");
        stmt.execute("CREATE TABLE ipo_log (stock_id VARCHAR(10) PRIMARY KEY, ipo_price REAL, available_shares REAL, demand_shares REAL, oversubscription_ratio REAL);");
        stmt.execute("DROP TABLE IF EXISTS ipo_subscription_log;");
        stmt.execute("CREATE TABLE ipo_subscription_log (stock_id VARCHAR(10), trader_id INT, demand_shares REAL, allocated_shares REAL, PRIMARY KEY (stock_id, trader_id));");

        // 【【修改 V4.28】】 新增 reserved_cash
        stmt.execute("DROP TABLE IF EXISTS trader_log;");
        stmt.execute("CREATE TABLE trader_log (" +
                "day INT, trader_id INT, trader_type TEXT, " +
                "risk_tolerance REAL, " +
                "max_stocks INT, " +
                "cash REAL, " + // 可用现金
                "reserved_cash REAL, " + // 冻结现金
                "stock_value REAL, " +
                "total_assets REAL, " + // (cash + reserved + stock_value)
                "PRIMARY KEY (day, trader_id)" +
                ");");

        psMarketLog = conn.prepareStatement("INSERT INTO market_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        psStockLog = conn.prepareStatement("INSERT INTO stock_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        psHoldingsLog = conn.prepareStatement("INSERT INTO holdings_log VALUES (?, ?, ?, ?);");
        psIPOLog = conn.prepareStatement("INSERT INTO ipo_log VALUES (?, ?, ?, ?, ?);");
        psIPOSubscriptionLog = conn.prepareStatement("INSERT INTO ipo_subscription_log VALUES (?, ?, ?, ?);");

        // 【【修改 V4.28】】 9 个字段
        psTraderLog = conn.prepareStatement("INSERT INTO trader_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);");

        conn.setAutoCommit(false);
        stmt.close();
    }

    // (V4.26 IPO 方法 - 保持不变)
    public void logIPO(String stockId, double ipoPrice, double available, double demand, double ratio) {
        if (conn == null) return;
        try {
            psIPOLog.setString(1, stockId);
            psIPOLog.setDouble(2, ipoPrice);
            psIPOLog.setDouble(3, available);
            psIPOLog.setDouble(4, demand);
            psIPOLog.setDouble(5, ratio);
            psIPOLog.addBatch();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void logIPOSubscription(String stockId, int traderId, double demandShares, double allocatedShares) {
        if (conn == null) return;
        try {
            psIPOSubscriptionLog.setString(1, stockId);
            psIPOSubscriptionLog.setInt(2, traderId);
            psIPOSubscriptionLog.setDouble(3, demandShares);
            psIPOSubscriptionLog.setDouble(4, allocatedShares);
            psIPOSubscriptionLog.addBatch();
        } catch (SQLException e) { e.printStackTrace(); }
    }
    public void commitIPO() {
        if (conn == null) return;
        try {
            psIPOLog.executeBatch();
            psIPOSubscriptionLog.executeBatch();
            conn.commit();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void setup(StockMarketSim sim) {
        this.traders = sim.traders;
        this.stocks = sim.stocks;
        this.market = sim.market;
    }

    /**
     * 【【V4.28 关键修复】】
     * 1. 修复了 V4.25 中导致数据丢失的 "..." 注释 Bug。
     * 2. 增加了对 'reservedCash' 的日志记录。
     * 3. 修复了 'total_assets' 的计算。
     */
    @Override
    public void step(SimState state) {
        if (conn == null || traders == null || stocks == null) return;
        int day = market.getCurrentDay();
        if (day == 0) return;
        if (state.schedule.getSteps() % market.STEPS_PER_DAY == 0) {
            System.out.println("DBLogger: Logging Day " + day);
        }

        try {
            // 1. 记录市场日志
            psMarketLog.setInt(1, day);
            psMarketLog.setDouble(2, market.indexOpen);
            psMarketLog.setDouble(3, market.indexHigh);
            psMarketLog.setDouble(4, market.indexLow);
            psMarketLog.setDouble(5, market.marketIndex);
            psMarketLog.setDouble(6, market.totalVolumeThisDay);
            psMarketLog.setDouble(7, market.totalTurnoverThisDay);
            psMarketLog.setDouble(8, market.marketTotalMarketCap);
            psMarketLog.setDouble(9, market.marketAmplitude);
            psMarketLog.setDouble(10, market.marketTurnoverRate);
            psMarketLog.addBatch();

            // 2. 记录所有股票
            for (int i = 0; i < stocks.size(); i++) {
                Stock s = (Stock) stocks.get(i);
                psStockLog.setInt(1, day);
                psStockLog.setString(2, s.stockId);
                psStockLog.setString(3, s.sector.name());
                psStockLog.setDouble(4, s.open);
                psStockLog.setDouble(5, s.high);
                psStockLog.setDouble(6, s.low);
                psStockLog.setDouble(7, s.currentPrice);
                psStockLog.setDouble(8, s.volumeThisDay);
                psStockLog.setDouble(9, s.turnoverThisDay);
                psStockLog.setDouble(10, s.pbRatio);
                psStockLog.setDouble(11, s.peRatioTTM);
                psStockLog.setDouble(12, s.eps);
                psStockLog.setDouble(13, s.netAssetsPerShare);
                psStockLog.setDouble(14, s.totalMarketCap);
                psStockLog.setDouble(15, s.liquidMarketCap);
                psStockLog.setDouble(16, s.turnoverRate);
                psStockLog.setDouble(17, s.amplitude);
                psStockLog.setDouble(18, s.peDynamic);
                psStockLog.setDouble(19, s.totalShares);
                psStockLog.setDouble(20, s.liquidShares);
                psStockLog.setDouble(21, s.high52w);
                psStockLog.setDouble(22, s.low52w);
                psStockLog.setDouble(23, s.peStatic);
                psStockLog.addBatch();
            }

            // 3. 记录所有交易员
            for (int i = 0; i < traders.size(); i++) {
                BaseTrader t = (BaseTrader) traders.get(i);
                double stockValue = t.portfolio.getTotalStockValue();
                double cash = t.portfolio.cash;
                double reservedCash = t.portfolio.reservedCash; // 【【V4.28 新增】】
                double totalAssets = cash + reservedCash + stockValue; // 【【V4.28 修复】】

                psTraderLog.setInt(1, day);
                psTraderLog.setInt(2, t.traderId);
                psTraderLog.setString(3, t.traderType);
                psTraderLog.setDouble(4, t.riskTolerance);
                psTraderLog.setInt(5, t.maxStocks);
                psTraderLog.setDouble(6, cash);
                psTraderLog.setDouble(7, reservedCash); // 【【V4.28 新增】】
                psTraderLog.setDouble(8, stockValue);
                psTraderLog.setDouble(9, totalAssets); // 【【V4.28 修复】】
                psTraderLog.addBatch();

                for (Map.Entry<Stock, Position> entry : t.portfolio.getPositions().entrySet()) {
                    psHoldingsLog.setInt(1, day);
                    psHoldingsLog.setInt(2, t.traderId);
                    psHoldingsLog.setString(3, entry.getKey().stockId);
                    psHoldingsLog.setDouble(4, entry.getValue().totalQuantity);
                    psHoldingsLog.addBatch();
                }
            }

            // 提交
            psMarketLog.executeBatch();
            psStockLog.executeBatch();
            psTraderLog.executeBatch();
            psHoldingsLog.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            System.err.println("CRITICAL LOGGER ERROR during step(): " + e.getMessage());
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }

    public void close() {
        try {
            if (psMarketLog != null) { psMarketLog.executeBatch(); psMarketLog.close(); }
            if (psStockLog != null) { psStockLog.executeBatch(); psStockLog.close(); }
            if (psTraderLog != null) { psTraderLog.executeBatch(); psTraderLog.close(); }
            if (psHoldingsLog != null) { psHoldingsLog.executeBatch(); psHoldingsLog.close(); }
            if (psIPOLog != null) { psIPOLog.close(); }
            if (psIPOSubscriptionLog != null) { psIPOSubscriptionLog.close(); }

            if (conn != null) {
                conn.commit();
                conn.close();
                System.out.println("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}