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

    private PreparedStatement psIPOLog;
    private PreparedStatement psIPOSubscriptionLog;

    private Bag traders;
    private Bag stocks;
    private Market market;
    private StockMarketSim sim; // 需要引用 sim 来获取 socialWealthPool

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

        // 【新增 V4.33】 市场日志增加 social_wealth_pool
        stmt.execute("DROP TABLE IF EXISTS market_log;");
        stmt.execute("CREATE TABLE market_log (day INT PRIMARY KEY, open REAL, high REAL, low REAL, close REAL, volume REAL, turnover REAL, total_market_cap REAL, amplitude REAL, turnover_rate REAL, social_wealth_pool REAL);");

        stmt.execute("DROP TABLE IF EXISTS stock_log;");
        stmt.execute("CREATE TABLE stock_log (day INT, stock_id VARCHAR(10), sector TEXT, open REAL, high REAL, low REAL, close REAL, volume REAL, turnover REAL, pb_ratio REAL, pe_ttm REAL, eps REAL, net_assets REAL, total_market_cap REAL, liquid_market_cap REAL, turnover_rate REAL, amplitude REAL, pe_dynamic REAL, total_shares REAL, liquid_shares REAL, high_52w REAL, low_52w REAL, pe_static REAL, PRIMARY KEY (day, stock_id));");
        stmt.execute("DROP TABLE IF EXISTS holdings_log;");
        stmt.execute("CREATE TABLE holdings_log (day INT, trader_id INT, stock_id VARCHAR(10), quantity REAL, PRIMARY KEY (day, trader_id, stock_id));");

        stmt.execute("DROP TABLE IF EXISTS ipo_log;");
        stmt.execute("CREATE TABLE ipo_log (stock_id VARCHAR(10) PRIMARY KEY, ipo_price REAL, available_shares REAL, demand_shares REAL, oversubscription_ratio REAL);");
        stmt.execute("DROP TABLE IF EXISTS ipo_subscription_log;");
        stmt.execute("CREATE TABLE ipo_subscription_log (stock_id VARCHAR(10), trader_id INT, demand_shares REAL, allocated_shares REAL, PRIMARY KEY (stock_id, trader_id));");

        // 【新增 V4.33】 增加 private_savings, is_active
        stmt.execute("DROP TABLE IF EXISTS trader_log;");
        stmt.execute("CREATE TABLE trader_log (" +
                "day INT, trader_id INT, trader_type TEXT, " +
                "risk_tolerance REAL, " +
                "max_stocks INT, " +
                "cash REAL, " +
                "reserved_cash REAL, " +
                "private_savings REAL, " + // 新增
                "stock_value REAL, " +
                "total_assets REAL, " +
                "is_active BOOLEAN, " + // 新增
                "PRIMARY KEY (day, trader_id)" +
                ");");

        psMarketLog = conn.prepareStatement("INSERT INTO market_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        psStockLog = conn.prepareStatement("INSERT INTO stock_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        psHoldingsLog = conn.prepareStatement("INSERT INTO holdings_log VALUES (?, ?, ?, ?);");
        psIPOLog = conn.prepareStatement("INSERT INTO ipo_log VALUES (?, ?, ?, ?, ?);");
        psIPOSubscriptionLog = conn.prepareStatement("INSERT INTO ipo_subscription_log VALUES (?, ?, ?, ?);");

        psTraderLog = conn.prepareStatement("INSERT INTO trader_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

        conn.setAutoCommit(false);
        stmt.close();
    }

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
        this.sim = sim;
        this.traders = sim.traders;
        this.stocks = sim.stocks;
        this.market = sim.market;
    }

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
            psMarketLog.setDouble(11, sim.socialWealthPool); // 新增
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
                Object obj = traders.get(i);
                if (!(obj instanceof BaseTrader)) continue;

                BaseTrader t = (BaseTrader) obj;
                // 如果是已死亡 Agent，可以选择记录最后一笔状态或者不记录
                // 为了数据连贯，这里选择记录，并标记 is_active=false

                double stockValue = t.portfolio.getTotalStockValue();
                double cash = t.portfolio.cash;
                double reservedCash = t.portfolio.reservedCash;
                double privateSavings = t.privateSavings; // 新增
                double totalAssets = cash + reservedCash + stockValue + privateSavings; // 注意：是否包含 savings 看统计口径，这里包含

                psTraderLog.setInt(1, day);
                psTraderLog.setInt(2, t.traderId);
                psTraderLog.setString(3, t.traderType);
                psTraderLog.setDouble(4, t.riskTolerance);
                psTraderLog.setInt(5, t.maxStocks);
                psTraderLog.setDouble(6, cash);
                psTraderLog.setDouble(7, reservedCash);
                psTraderLog.setDouble(8, privateSavings); // 新增
                psTraderLog.setDouble(9, stockValue);
                psTraderLog.setDouble(10, totalAssets);
                psTraderLog.setBoolean(11, t.isActive()); // 新增
                psTraderLog.addBatch();

                if (t.isActive()) { // 只记录活跃用户的持仓，减少数据量
                    for (Map.Entry<Stock, Position> entry : t.portfolio.getPositions().entrySet()) {
                        psHoldingsLog.setInt(1, day);
                        psHoldingsLog.setInt(2, t.traderId);
                        psHoldingsLog.setString(3, entry.getKey().stockId);
                        psHoldingsLog.setDouble(4, entry.getValue().totalQuantity);
                        psHoldingsLog.addBatch();
                    }
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