package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.TraderDetailDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.TraderDetailDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Trader analysis service - migrated from Python trader.py
 */
@Service
public class TraderAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    /**
     * Get list of all traders for a given day
     * SQL: SELECT * FROM trader_log WHERE day = ?
     */
    public List<TraderSummary> getTraderList(String dbFile, int day) throws SQLException {
        List<TraderSummary> traders = new ArrayList<>();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            String sql = "SELECT trader_id, trader_type, is_active, total_assets, " +
                    "private_savings, cash, stock_value " +
                    "FROM trader_log WHERE day = ? ORDER BY trader_id";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, day);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        traders.add(new TraderSummary(
                                rs.getInt("trader_id"),
                                rs.getString("trader_type"),
                                rs.getBoolean("is_active"),
                                rs.getDouble("total_assets"),
                                rs.getDouble("private_savings"),
                                rs.getDouble("cash"),
                                rs.getDouble("stock_value")));
                    }
                }
            }
        }
        return traders;
    }

    /**
     * Get detailed trader analysis data
     * Equivalent to Python trader.py: update_trader_detail()
     */
    public TraderDetailDTO getTraderDetail(String dbFile, int traderId, int day) throws SQLException {
        TraderDetailDTO result = new TraderDetailDTO();
        result.traderId = traderId;

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            // Get historical data
            result.history = getTraderHistory(conn, traderId);

            // Get current metrics
            result.currentMetrics = getCurrentMetrics(conn, traderId, day, result.history);

            // Get trader type and status
            TraderSummary summary = getTraderSummary(conn, traderId, day);
            if (summary != null) {
                result.traderType = summary.traderType;
                result.isActive = summary.isActive;
            }

            // Get current holdings
            result.holdings = getHoldings(conn, traderId, day);
        }

        return result;
    }

    /**
     * Get trader history data
     * SQL: SELECT day, total_assets, private_savings, cash, reserved_cash,
     * stock_value, risk_tolerance
     * FROM trader_log WHERE trader_id = ? ORDER BY day
     */
    private List<TraderDayData> getTraderHistory(Connection conn, int traderId) throws SQLException {
        List<TraderDayData> history = new ArrayList<>();
        String sql = "SELECT day, total_assets, private_savings, cash, reserved_cash, " +
                "stock_value, risk_tolerance, is_active " +
                "FROM trader_log WHERE trader_id = ? ORDER BY day";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, traderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TraderDayData data = new TraderDayData();
                    data.day = rs.getInt("day");
                    data.totalAssets = rs.getDouble("total_assets");
                    data.privateSavings = rs.getDouble("private_savings");
                    data.cash = rs.getDouble("cash");
                    data.reservedCash = rs.getDouble("reserved_cash");
                    data.stockValue = rs.getDouble("stock_value");
                    data.riskTolerance = rs.getDouble("risk_tolerance");
                    data.isActive = rs.getBoolean("is_active");
                    history.add(data);
                }
            }
        }
        return history;
    }

    /**
     * Get current metrics for a trader, including daily PnL calculation
     */
    private TraderMetrics getCurrentMetrics(Connection conn, int traderId, int day, List<TraderDayData> history) {
        // Find current day data
        TraderDayData current = null;
        TraderDayData previous = null;

        for (TraderDayData data : history) {
            if (data.day == day) {
                current = data;
            } else if (data.day == day - 1) {
                previous = data;
            }
        }

        if (current == null) {
            return null;
        }

        // Calculate daily PnL
        double dailyPnl = 0;
        if (previous != null) {
            dailyPnl = current.totalAssets - previous.totalAssets;
        }

        return new TraderMetrics(
                current.totalAssets,
                dailyPnl,
                current.privateSavings,
                current.cash,
                current.reservedCash,
                current.stockValue,
                current.riskTolerance);
    }

    /**
     * Get trader summary for a specific day
     */
    private TraderSummary getTraderSummary(Connection conn, int traderId, int day) throws SQLException {
        String sql = "SELECT trader_id, trader_type, is_active, total_assets, " +
                "private_savings, cash, stock_value " +
                "FROM trader_log WHERE trader_id = ? AND day = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, traderId);
            ps.setInt(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TraderSummary(
                            rs.getInt("trader_id"),
                            rs.getString("trader_type"),
                            rs.getBoolean("is_active"),
                            rs.getDouble("total_assets"),
                            rs.getDouble("private_savings"),
                            rs.getDouble("cash"),
                            rs.getDouble("stock_value"));
                }
            }
        }
        return null;
    }

    /**
     * Get trader holdings
     * SQL: SELECT h.stock_id, h.quantity, s.close
     * FROM holdings_log h JOIN stock_log s ON h.stock_id=s.stock_id AND h.day=s.day
     * WHERE h.trader_id=? AND h.day=?
     */
    private List<Holding> getHoldings(Connection conn, int traderId, int day) throws SQLException {
        List<Holding> holdings = new ArrayList<>();
        String sql = "SELECT h.stock_id, h.quantity, s.close " +
                "FROM holdings_log h " +
                "JOIN stock_log s ON h.stock_id = s.stock_id AND h.day = s.day " +
                "WHERE h.trader_id = ? AND h.day = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, traderId);
            ps.setInt(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double quantity = rs.getDouble("quantity");
                    double price = rs.getDouble("close");
                    holdings.add(new Holding(
                            rs.getString("stock_id"),
                            quantity,
                            price,
                            quantity * price));
                }
            }
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
