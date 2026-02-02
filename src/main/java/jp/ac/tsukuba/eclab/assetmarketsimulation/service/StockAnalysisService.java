package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StockDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.StockDetailDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock analysis service - migrated from Python stock.py
 */
@Service
public class StockAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    /**
     * Get list of all stocks for a given day
     * SQL: SELECT stock_id, sector, close, pe_ttm, total_market_cap FROM stock_log
     * WHERE day = ?
     */
    public List<StockSummary> getStockList(String dbFile, int day) throws SQLException {
        List<StockSummary> stocks = new ArrayList<>();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            String sql = "SELECT stock_id, sector, close, pe_ttm, total_market_cap " +
                    "FROM stock_log WHERE day = ? ORDER BY stock_id";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, day);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stocks.add(new StockSummary(
                                rs.getString("stock_id"),
                                rs.getString("sector"),
                                rs.getDouble("close"),
                                rs.getDouble("pe_ttm"),
                                rs.getDouble("total_market_cap")));
                    }
                }
            }
        }
        return stocks;
    }

    /**
     * Get detailed stock analysis data
     * Equivalent to Python stock.py: update_stock_detail()
     */
    public StockDetailDTO getStockDetail(String dbFile, String stockId, int day) throws SQLException {
        StockDetailDTO result = new StockDetailDTO();
        result.stockId = stockId;

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            // Get historical data
            result.history = getStockHistory(conn, stockId);

            // Get current metrics
            result.currentMetrics = getCurrentMetrics(conn, stockId, day);

            // Get sector
            if (!result.history.isEmpty()) {
                result.sector = getSector(conn, stockId);
            }

            // Get shareholders
            result.shareholders = getShareholders(conn, stockId, day);
        }

        return result;
    }

    /**
     * Get stock history data
     * SQL: SELECT * FROM stock_log WHERE stock_id = ? ORDER BY day
     */
    private List<StockDayData> getStockHistory(Connection conn, String stockId) throws SQLException {
        List<StockDayData> history = new ArrayList<>();
        String sql = "SELECT day, open, high, low, close, volume, turnover, " +
                "pb_ratio, pe_ttm, pe_static, pe_dynamic, eps, net_assets, " +
                "total_market_cap, liquid_market_cap, turnover_rate, amplitude, " +
                "total_shares, liquid_shares, high_52w, low_52w " +
                "FROM stock_log WHERE stock_id = ? ORDER BY day";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StockDayData data = new StockDayData();
                    data.day = rs.getInt("day");
                    data.open = rs.getDouble("open");
                    data.high = rs.getDouble("high");
                    data.low = rs.getDouble("low");
                    data.close = rs.getDouble("close");
                    data.volume = rs.getDouble("volume");
                    data.turnover = rs.getDouble("turnover");
                    data.pbRatio = rs.getDouble("pb_ratio");
                    data.peTtm = rs.getDouble("pe_ttm");
                    data.peStatic = rs.getDouble("pe_static");
                    data.peDynamic = rs.getDouble("pe_dynamic");
                    data.eps = rs.getDouble("eps");
                    data.netAssets = rs.getDouble("net_assets");
                    data.totalMarketCap = rs.getDouble("total_market_cap");
                    data.liquidMarketCap = rs.getDouble("liquid_market_cap");
                    data.turnoverRate = rs.getDouble("turnover_rate");
                    data.amplitude = rs.getDouble("amplitude");
                    data.totalShares = rs.getDouble("total_shares");
                    data.liquidShares = rs.getDouble("liquid_shares");
                    data.high52w = rs.getDouble("high_52w");
                    data.low52w = rs.getDouble("low_52w");
                    history.add(data);
                }
            }
        }
        return history;
    }

    /**
     * Get current metrics for a stock on a specific day
     */
    private StockMetrics getCurrentMetrics(Connection conn, String stockId, int day) throws SQLException {
        String sql = "SELECT close, pe_ttm, pb_ratio, total_market_cap, volume, turnover_rate " +
                "FROM stock_log WHERE stock_id = ? AND day = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockId);
            ps.setInt(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StockMetrics(
                            rs.getDouble("close"),
                            rs.getDouble("pe_ttm"),
                            rs.getDouble("pb_ratio"),
                            rs.getDouble("total_market_cap"),
                            rs.getDouble("volume"),
                            rs.getDouble("turnover_rate"));
                }
            }
        }
        return null;
    }

    /**
     * Get stock sector
     */
    private String getSector(Connection conn, String stockId) throws SQLException {
        String sql = "SELECT sector FROM stock_log WHERE stock_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("sector");
                }
            }
        }
        return null;
    }

    /**
     * Get top shareholders of a stock
     * SQL: SELECT h.trader_id, t.trader_type, h.quantity, (h.quantity * s.close) as
     * value
     * FROM holdings_log h JOIN stock_log s ON h.stock_id = s.stock_id AND h.day =
     * s.day
     * LEFT JOIN trader_log t ON h.trader_id = t.trader_id AND t.day = h.day
     * WHERE h.stock_id = ? AND h.day = ? ORDER BY h.quantity DESC LIMIT 50
     */
    private List<Shareholder> getShareholders(Connection conn, String stockId, int day) throws SQLException {
        List<Shareholder> shareholders = new ArrayList<>();
        String sql = "SELECT h.trader_id, t.trader_type, h.quantity, (h.quantity * s.close) as value " +
                "FROM holdings_log h " +
                "JOIN stock_log s ON h.stock_id = s.stock_id AND h.day = s.day " +
                "LEFT JOIN trader_log t ON h.trader_id = t.trader_id AND t.day = h.day " +
                "WHERE h.stock_id = ? AND h.day = ? ORDER BY h.quantity DESC LIMIT 50";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockId);
            ps.setInt(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shareholders.add(new Shareholder(
                            rs.getInt("trader_id"),
                            rs.getString("trader_type"),
                            rs.getDouble("quantity"),
                            rs.getDouble("value")));
                }
            }
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
