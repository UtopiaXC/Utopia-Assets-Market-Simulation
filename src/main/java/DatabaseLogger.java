import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Map;

/**
 * 数据库日志记录器 (使用 SQLite)
 * (已重构：记录 OHLC 和详细持仓)
 */
public class DatabaseLogger implements Steppable {

    private Connection conn;
    private PreparedStatement psTraderLog;
    private PreparedStatement psStockLog;
    private PreparedStatement psMarketLog;
    private PreparedStatement psHoldingsLog; // 新增：持仓日志

    private Bag traders;
    private Bag stocks;
    private Market market;

    public DatabaseLogger(long seed) {
        String dbName = String.format("simulation_data_seed_%d.db", seed);
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
            System.out.println("SQLite 数据库已连接: " + dbName);
            initializeDatabase();
        } catch (Exception e) {
            System.err.println("DatabaseLogger 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDatabase() throws SQLException {
        Statement stmt = conn.createStatement();

        // 1. 市场日志 (每日K线)
        stmt.execute("DROP TABLE IF EXISTS market_log;");
        stmt.execute("CREATE TABLE market_log (" +
                "day INT PRIMARY KEY, " +
                "open REAL, " +
                "high REAL, " +
                "low REAL, " +
                "close REAL, " +
                "volume REAL, " +
                "turnover REAL" +
                ");");

        // 2. 股票日志 (每日K线)
        stmt.execute("DROP TABLE IF EXISTS stock_log;");
        stmt.execute("CREATE TABLE stock_log (" +
                "day INT, " +
                "stock_id VARCHAR(10), " +
                "open REAL, " +
                "high REAL, " +
                "low REAL, " +
                "close REAL, " +
                "volume REAL, " +
                "turnover REAL, " +
                "pb_ratio REAL, " +
                "pe_ttm REAL, " +
                "PRIMARY KEY (day, stock_id)" +
                ");");

        // 3. 交易员日志 (每日总结)
        stmt.execute("DROP TABLE IF EXISTS trader_log;");
        stmt.execute("CREATE TABLE trader_log (" +
                "day INT, " +
                "trader_id INT, " +
                "risk_tolerance REAL, " +
                "trading_frequency REAL, " + // 新增
                "cash REAL, " +
                "stock_value REAL, " +
                "total_assets REAL, " +
                "PRIMARY KEY (day, trader_id)" +
                ");");

        // 4. 持仓日志 (每日详细)
        stmt.execute("DROP TABLE IF EXISTS holdings_log;");
        stmt.execute("CREATE TABLE holdings_log (" +
                "day INT, " +
                "trader_id INT, " +
                "stock_id VARCHAR(10), " +
                "quantity REAL, " +
                "PRIMARY KEY (day, trader_id, stock_id)" +
                ");");

        // 准备 Statements
        psMarketLog = conn.prepareStatement("INSERT INTO market_log VALUES (?, ?, ?, ?, ?, ?, ?);");
        psStockLog = conn.prepareStatement("INSERT INTO stock_log VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        psTraderLog = conn.prepareStatement("INSERT INTO trader_log VALUES (?, ?, ?, ?, ?, ?, ?);");
        psHoldingsLog = conn.prepareStatement("INSERT INTO holdings_log VALUES (?, ?, ?, ?);");

        conn.setAutoCommit(false);
        stmt.close();
    }

    public void setup(StockMarketSim sim) {
        this.traders = sim.traders;
        this.stocks = sim.stocks;
        this.market = sim.market;
    }

    @Override
    public void step(SimState state) {
        // 此方法在每日收盘后 (每 22 步) 被调用一次
        if (conn == null || traders == null || stocks == null) {
            return;
        }

        int day = market.getCurrentDay();
        if (day == 0) return;

        System.out.println("DBLogger: 正在记录 Day " + day);

        try {
            // 1. 记录市场日志
            psMarketLog.setInt(1, day);
            psMarketLog.setDouble(2, market.indexOpen);
            psMarketLog.setDouble(3, market.indexHigh);
            psMarketLog.setDouble(4, market.indexLow);
            psMarketLog.setDouble(5, market.marketIndex); // 收盘指数
            psMarketLog.setDouble(6, market.totalVolumeThisDay);
            psMarketLog.setDouble(7, market.totalTurnoverThisDay);
            psMarketLog.addBatch();

            // 2. 记录所有股票 (收盘价)
            for (int i = 0; i < stocks.size(); i++) {
                Stock s = (Stock) stocks.get(i);
                psStockLog.setInt(1, day);
                psStockLog.setString(2, s.stockId);
                psStockLog.setDouble(3, s.open);
                psStockLog.setDouble(4, s.high);
                psStockLog.setDouble(5, s.low);
                psStockLog.setDouble(6, s.currentPrice); // 收盘价
                psStockLog.setDouble(7, s.volumeThisDay);
                psStockLog.setDouble(8, s.turnoverThisDay);
                psStockLog.setDouble(9, s.pbRatio);
                psStockLog.setDouble(10, s.peRatioTTM);
                psStockLog.addBatch();
            }

            // 3. 记录所有交易员 (收盘资产)
            for (int i = 0; i < traders.size(); i++) {
                RiskBasedTrader t = (RiskBasedTrader) traders.get(i);
                double stockValue = t.portfolio.getTotalStockValue();
                double cash = t.portfolio.cash;

                // 3a. 写入 trader_log
                psTraderLog.setInt(1, day);
                psTraderLog.setInt(2, t.traderId);
                psTraderLog.setDouble(3, t.riskTolerance);
                psTraderLog.setDouble(4, t.tradingFrequency);
                psTraderLog.setDouble(5, cash);
                psTraderLog.setDouble(6, stockValue);
                psTraderLog.setDouble(7, cash + stockValue);
                psTraderLog.addBatch();

                // 3b. 写入 holdings_log (持仓详情)
                for (Map.Entry<Stock, Double> entry : t.portfolio.getPositions().entrySet()) {
                    psHoldingsLog.setInt(1, day);
                    psHoldingsLog.setInt(2, t.traderId);
                    psHoldingsLog.setString(3, entry.getKey().stockId);
                    psHoldingsLog.setDouble(4, entry.getValue()); // 数量
                    psHoldingsLog.addBatch();
                }
            }

            // 提交本“日”数据
            psMarketLog.executeBatch();
            psStockLog.executeBatch();
            psTraderLog.executeBatch();
            psHoldingsLog.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }

    public void close() {
        try {
            // 在关闭前，执行最后一次提交
            if (psMarketLog != null) { psMarketLog.executeBatch(); psMarketLog.close(); }
            if (psStockLog != null) { psStockLog.executeBatch(); psStockLog.close(); }
            if (psTraderLog != null) { psTraderLog.executeBatch(); psTraderLog.close(); }
            if (psHoldingsLog != null) { psHoldingsLog.executeBatch(); psHoldingsLog.close(); }

            if (conn != null) {
                conn.commit();
                conn.close();
                System.out.println("SQLite 数据库已关闭。");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}