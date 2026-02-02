package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.SectorStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.SectorStatsDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sector statistics service - migrated from Python sector.py
 */
@Service
public class SectorAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    /**
     * Get sector statistics data
     * Equivalent to Python sector.py: update_sector()
     */
    public SectorStatsDTO getSectorStats(String dbFile) throws SQLException {
        SectorStatsDTO result = new SectorStatsDTO();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            // Query both in one pass for efficiency
            String sql = "SELECT day, sector, SUM(total_market_cap) as cap, AVG(pe_ttm) as pe " +
                    "FROM stock_log GROUP BY day, sector ORDER BY day, sector";

            List<SectorData> marketCapData = new ArrayList<>();
            List<SectorData> peData = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int day = rs.getInt("day");
                    String sector = rs.getString("sector");

                    marketCapData.add(new SectorData(day, sector, rs.getDouble("cap")));
                    peData.add(new SectorData(day, sector, rs.getDouble("pe")));
                }
            }

            result.marketCapHistory = marketCapData;
            result.peHistory = peData;
        }

        return result;
    }

    /**
     * Get list of all sectors
     */
    public List<String> getSectorList(String dbFile) throws SQLException {
        List<String> sectors = new ArrayList<>();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            String sql = "SELECT DISTINCT sector FROM stock_log ORDER BY sector";

            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sectors.add(rs.getString("sector"));
                }
            }
        }
        return sectors;
    }

    /**
     * Get sector stocks for a specific day
     */
    public List<SectorStockInfo> getSectorStocks(String dbFile, String sector, int day) throws SQLException {
        List<SectorStockInfo> stocks = new ArrayList<>();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            String sql = "SELECT stock_id, close, pe_ttm, total_market_cap, turnover " +
                    "FROM stock_log WHERE sector = ? AND day = ? ORDER BY total_market_cap DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sector);
                ps.setInt(2, day);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stocks.add(new SectorStockInfo(
                                rs.getString("stock_id"),
                                rs.getDouble("close"),
                                rs.getDouble("pe_ttm"),
                                rs.getDouble("total_market_cap"),
                                rs.getDouble("turnover")));
                    }
                }
            }
        }
        return stocks;
    }

    /**
     * Sector stock info DTO
     */
    public static class SectorStockInfo {
        public String stockId;
        public double close;
        public double peTtm;
        public double totalMarketCap;
        public double turnover;

        public SectorStockInfo(String stockId, double close, double peTtm,
                double totalMarketCap, double turnover) {
            this.stockId = stockId;
            this.close = close;
            this.peTtm = peTtm;
            this.totalMarketCap = totalMarketCap;
            this.turnover = turnover;
        }
    }
}
