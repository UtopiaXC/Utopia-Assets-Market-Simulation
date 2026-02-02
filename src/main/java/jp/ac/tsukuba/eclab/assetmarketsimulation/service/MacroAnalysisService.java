package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MacroStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MacroStatsDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Macro statistics service - migrated from Python macro.py
 */
@Service
public class MacroAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    /**
     * Get macro statistics data
     * Equivalent to Python macro.py: update_macro()
     */
    public MacroStatsDTO getMacroStats(String dbFile) throws SQLException {
        MacroStatsDTO result = new MacroStatsDTO();

        try (Connection conn = databaseService.getConnectionByName(dbFile)) {
            result.populationHistory = getPopulationHistory(conn);
            result.wealthHistory = getWealthHistory(conn);
            result.agentTypeAssets = getAgentTypeAssets(conn);
            result.agentTypeRisk = getAgentTypeRisk(conn);
        }

        return result;
    }

    /**
     * Get active agent population over time
     * SQL: SELECT day, COUNT(*) as count FROM trader_log WHERE is_active=1 GROUP BY
     * day
     */
    private List<PopulationData> getPopulationHistory(Connection conn) throws SQLException {
        List<PopulationData> data = new ArrayList<>();
        String sql = "SELECT day, COUNT(*) as count FROM trader_log WHERE is_active = 1 GROUP BY day ORDER BY day";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.add(new PopulationData(
                        rs.getInt("day"),
                        rs.getInt("count")));
            }
        }
        return data;
    }

    /**
     * Get macro wealth structure over time
     * Combines data from market_log (social pool) and trader_log (savings, total
     * assets)
     */
    private List<WealthData> getWealthHistory(Connection conn) throws SQLException {
        List<WealthData> data = new ArrayList<>();

        // Join market and trader aggregates
        String sql = "SELECT m.day, m.social_wealth_pool, " +
                "t.sav as savings, (t.tot - t.sav) as liquidity " +
                "FROM market_log m " +
                "LEFT JOIN (" +
                "  SELECT day, SUM(private_savings) as sav, SUM(total_assets) as tot " +
                "  FROM trader_log GROUP BY day" +
                ") t ON m.day = t.day " +
                "ORDER BY m.day";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.add(new WealthData(
                        rs.getInt("day"),
                        rs.getDouble("social_wealth_pool"),
                        rs.getDouble("savings"),
                        rs.getDouble("liquidity")));
            }
        }
        return data;
    }

    /**
     * Get total assets by agent type over time
     * SQL: SELECT day, trader_type, SUM(total_assets) as v FROM trader_log GROUP BY
     * day, trader_type
     */
    private List<AgentTypeData> getAgentTypeAssets(Connection conn) throws SQLException {
        List<AgentTypeData> data = new ArrayList<>();
        String sql = "SELECT day, trader_type, SUM(total_assets) as value " +
                "FROM trader_log GROUP BY day, trader_type ORDER BY day, trader_type";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.add(new AgentTypeData(
                        rs.getInt("day"),
                        rs.getString("trader_type"),
                        rs.getDouble("value")));
            }
        }
        return data;
    }

    /**
     * Get average risk tolerance by agent type over time
     * SQL: SELECT day, trader_type, AVG(risk_tolerance) as v
     * FROM trader_log WHERE is_active=1 GROUP BY day, trader_type
     */
    private List<AgentTypeData> getAgentTypeRisk(Connection conn) throws SQLException {
        List<AgentTypeData> data = new ArrayList<>();
        String sql = "SELECT day, trader_type, AVG(risk_tolerance) as value " +
                "FROM trader_log WHERE is_active = 1 GROUP BY day, trader_type ORDER BY day, trader_type";

        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                data.add(new AgentTypeData(
                        rs.getInt("day"),
                        rs.getString("trader_type"),
                        rs.getDouble("value")));
            }
        }
        return data;
    }
}
