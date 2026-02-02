package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Market analysis service - migrated from Python market.py
 */
@Service
public class MarketAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    /**
     * Get market overview data including K-line, day details, and top stocks
     * Equivalent to Python market.py: update_market_tab()
     */
    public MarketOverviewDTO getMarketOverview(String dbFile, int day) throws SQLException {
        MarketOverviewDTO result = new MarketOverviewDTO();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            result.klineData = getKlineData(conn);
            result.dayDetail = getDayDetail(conn, day);
            result.topActiveStocks = getTopActiveStocks(conn, day);
        }

        return result;
    }

    /**
     * Get all market K-line data
     * SQL: SELECT * FROM market_log
     */
    public List<MarketDayData> getKlineData(Connection conn) throws SQLException {
        List<MarketDayData> data = new ArrayList<>();
        String sql = "SELECT day, open, high, low, close, volume, turnover, " +
                "total_market_cap, amplitude, turnover_rate, social_wealth_pool " +
                "FROM market_log ORDER BY day";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.add(new MarketDayData(
                        rs.getInt("day"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getDouble("volume"),
                        rs.getDouble("turnover"),
                        rs.getDouble("total_market_cap"),
                        rs.getDouble("amplitude"),
                        rs.getDouble("turnover_rate"),
                        rs.getDouble("social_wealth_pool")));
            }
        }
        return data;
    }

    /**
     * Get market details for a specific day
     */
    public MarketDayDetail getDayDetail(Connection conn, int day) throws SQLException {
        String sql = "SELECT day, close, volume, turnover, turnover_rate, social_wealth_pool " +
                "FROM market_log WHERE day = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, day);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MarketDayDetail(
                            rs.getInt("day"),
                            rs.getDouble("close"),
                            rs.getDouble("volume"),
                            rs.getDouble("turnover"),
                            rs.getDouble("turnover_rate"),
                            rs.getDouble("social_wealth_pool"));
                }
            }
        }
        return null;
    }

    /**
     * Get top 10 active stocks by turnover
     * SQL: SELECT stock_id, sector, close, turnover, volume, turnover_rate
     * FROM stock_log WHERE day = ? ORDER BY turnover DESC LIMIT 10
     */
    public List<TopStock> getTopActiveStocks(Connection conn, int day) throws SQLException {
        List<TopStock> stocks = new ArrayList<>();
        String sql = "SELECT stock_id, sector, close, turnover, volume, turnover_rate " +
                "FROM stock_log WHERE day = ? ORDER BY turnover DESC LIMIT 10";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, day);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    stocks.add(new TopStock(
                            rank++,
                            rs.getString("stock_id"),
                            rs.getString("sector"),
                            rs.getDouble("close"),
                            rs.getDouble("turnover"),
                            rs.getDouble("volume"),
                            rs.getDouble("turnover_rate")));
                }
            }
        }
        return stocks;
    }

    /**
     * Get total number of simulation days
     */
    public int getTotalDays(String dbFile) throws SQLException {
        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            String sql = "SELECT MAX(day) as max_day FROM market_log";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("max_day");
                }
            }
        }
        return 0;
    }
}
