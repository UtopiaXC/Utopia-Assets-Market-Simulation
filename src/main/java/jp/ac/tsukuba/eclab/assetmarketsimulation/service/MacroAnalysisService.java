package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MacroStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MacroStatsDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentAssetDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.MarketDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.AgentAssetDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.MarketDailyMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentTypeDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.AgentTypeDailyMapper;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Macro statistics service - optimized with pre-aggregated data and caching
 */
@Service
public class MacroAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    // Cache by dbPath -> (lastModified, MacroStatsDTO)
    private final Map<String, CacheEntry> macroCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final long lastModified;
        final MacroStatsDTO data;
        CacheEntry(long lastModified, MacroStatsDTO data) {
            this.lastModified = lastModified;
            this.data = data;
        }
    }

    public MacroStatsDTO getMacroStats(String dbFile) {
        String dbPath = resolveDbPath(dbFile);
        File file = new File(dbPath);
        long currentMod = file.exists() ? file.lastModified() : 0;

        CacheEntry cached = macroCache.get(dbPath);
        if (cached != null && cached.lastModified == currentMod && currentMod > 0) {
            return cached.data;
        }

        MacroStatsDTO result = computeMacroStats(dbFile);
        if (currentMod > 0) {
            macroCache.put(dbPath, new CacheEntry(currentMod, result));
        }
        return result;
    }

    public List<PopulationData> getPopulation(String dbFile) {
        // Fast path: if cached, return directly
        String dbPath = resolveDbPath(dbFile);
        File file = new File(dbPath);
        long currentMod = file.exists() ? file.lastModified() : 0;
        CacheEntry cached = macroCache.get(dbPath);
        if (cached != null && cached.lastModified == currentMod && currentMod > 0) {
            return cached.data.populationHistory;
        }

        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);
            List<MarketDailyEntity> marketData = marketMapper.selectAll();
            if (!marketData.isEmpty() && marketData.get(0).getActiveAgents() != null) {
                List<PopulationData> pop = new ArrayList<>(marketData.size());
                for (MarketDailyEntity m : marketData) {
                    pop.add(new PopulationData(m.getDay(), m.getActiveAgents() != null ? m.getActiveAgents() : 0));
                }
                return pop;
            }
        } catch (Exception ignored) {}

        // Fallback to full stats
        return getMacroStats(dbFile).populationHistory;
    }

    public List<WealthData> getWealth(String dbFile) {
        // Fast path: if cached, return directly
        String dbPath = resolveDbPath(dbFile);
        File file = new File(dbPath);
        long currentMod = file.exists() ? file.lastModified() : 0;
        CacheEntry cached = macroCache.get(dbPath);
        if (cached != null && cached.lastModified == currentMod && currentMod > 0) {
            return cached.data.wealthHistory;
        }

        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);
            List<MarketDailyEntity> marketData = marketMapper.selectAll();
            if (!marketData.isEmpty() && marketData.get(0).getTotalAgentAssets() != null) {
                List<WealthData> wealth = new ArrayList<>(marketData.size());
                for (MarketDailyEntity m : marketData) {
                    double savings = m.getTotalPrivateSavings() != null ? m.getTotalPrivateSavings() : 0;
                    double totalAssets = m.getTotalAgentAssets() != null ? m.getTotalAgentAssets() : 0;
                    wealth.add(new WealthData(
                            m.getDay(),
                            m.getSocialWealthPool() != null ? m.getSocialWealthPool() : 0,
                            savings,
                            totalAssets - savings));
                }
                return wealth;
            }
        } catch (Exception ignored) {}

        // Fallback to full stats
        return getMacroStats(dbFile).wealthHistory;
    }

    public List<AgentTypeData> getAgentAssets(String dbFile) {
        return getMacroStats(dbFile).agentTypeAssets;
    }

    public List<AgentTypeData> getAgentRisk(String dbFile) {
        return getMacroStats(dbFile).agentTypeRisk;
    }

    private MacroStatsDTO computeMacroStats(String dbFile) {
        MacroStatsDTO result = new MacroStatsDTO();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);
            List<MarketDailyEntity> marketData = marketMapper.selectAll();

            // 1. Population history - use market_daily.active_agents (O(days))
            result.populationHistory = new ArrayList<>(marketData.size());
            boolean hasActiveAgentsInMarket = !marketData.isEmpty() && marketData.get(0).getActiveAgents() != null;
            if (hasActiveAgentsInMarket) {
                for (MarketDailyEntity m : marketData) {
                    result.populationHistory.add(new PopulationData(
                            m.getDay(), m.getActiveAgents() != null ? m.getActiveAgents() : 0));
                }
            } else {
                // Legacy fallback
                AgentAssetDailyMapper agentMapper = session.getMapper(AgentAssetDailyMapper.class);
                List<AgentAssetDailyEntity> activeCount = agentMapper.selectActiveCountByDay();
                for (AgentAssetDailyEntity e : activeCount) {
                    result.populationHistory.add(new PopulationData(
                            e.getDay(), e.getActiveAgents() != null ? e.getActiveAgents() : 0));
                }
            }

            // 2. Wealth history - check if total_agent_assets is pre-aggregated
            boolean hasWealthInMarket = !marketData.isEmpty() && marketData.get(0).getTotalAgentAssets() != null;
            result.wealthHistory = new ArrayList<>(marketData.size());
            if (hasWealthInMarket) {
                for (MarketDailyEntity m : marketData) {
                    double savings = m.getTotalPrivateSavings() != null ? m.getTotalPrivateSavings() : 0;
                    double totalAssets = m.getTotalAgentAssets() != null ? m.getTotalAgentAssets() : 0;
                    result.wealthHistory.add(new WealthData(
                            m.getDay(),
                            m.getSocialWealthPool() != null ? m.getSocialWealthPool() : 0,
                            savings,
                            totalAssets - savings));
                }
            } else {
                // Legacy fallback: scan agent_asset_daily
                AgentAssetDailyMapper agentMapper = session.getMapper(AgentAssetDailyMapper.class);
                List<AgentAssetDailyEntity> wealthData = agentMapper.selectWealthAggregateByDay();
                Map<Integer, AgentAssetDailyEntity> wealthMap = new HashMap<>();
                for (AgentAssetDailyEntity e : wealthData) {
                    wealthMap.put(e.getDay(), e);
                }
                for (MarketDailyEntity m : marketData) {
                    AgentAssetDailyEntity w = wealthMap.get(m.getDay());
                    double savings = w != null && w.getPrivateSavings() != null ? w.getPrivateSavings() : 0;
                    double totalAssets = w != null && w.getTotalAssets() != null ? w.getTotalAssets() : 0;
                    result.wealthHistory.add(new WealthData(
                            m.getDay(),
                            m.getSocialWealthPool() != null ? m.getSocialWealthPool() : 0,
                            savings,
                            totalAssets - savings));
                }
            }

            // 3. Agent type assets & risk - check agent_type_daily table
            List<AgentTypeDailyEntity> typeDailyList = null;
            try {
                AgentTypeDailyMapper typeMapper = session.getMapper(AgentTypeDailyMapper.class);
                if (typeMapper.count() > 0) {
                    typeDailyList = typeMapper.selectAll();
                }
            } catch (Exception ignored) {}

            if (typeDailyList != null && !typeDailyList.isEmpty()) {
                result.agentTypeAssets = new ArrayList<>(typeDailyList.size());
                result.agentTypeRisk = new ArrayList<>(typeDailyList.size());
                for (AgentTypeDailyEntity e : typeDailyList) {
                    result.agentTypeAssets.add(new AgentTypeData(
                            e.getDay(),
                            e.getAgentType() != null ? e.getAgentType() : "",
                            e.getTotalAssets() != null ? e.getTotalAssets() : 0));
                    result.agentTypeRisk.add(new AgentTypeData(
                            e.getDay(),
                            e.getAgentType() != null ? e.getAgentType() : "",
                            e.getAvgRiskTolerance() != null ? e.getAvgRiskTolerance() : 0));
                }
            } else {
                // Legacy fallback
                AgentAssetDailyMapper agentMapper = session.getMapper(AgentAssetDailyMapper.class);
                List<AgentAssetDailyEntity> typeAssets = agentMapper.selectAssetsByTypeAndDay();
                result.agentTypeAssets = new ArrayList<>();
                for (AgentAssetDailyEntity e : typeAssets) {
                    result.agentTypeAssets.add(new AgentTypeData(
                            e.getDay(),
                            e.getAgentType() != null ? e.getAgentType() : "",
                            e.getTotalAssets() != null ? e.getTotalAssets() : 0));
                }

                List<AgentAssetDailyEntity> typeRisk = agentMapper.selectAvgRiskByTypeAndDay();
                result.agentTypeRisk = new ArrayList<>();
                for (AgentAssetDailyEntity e : typeRisk) {
                    result.agentTypeRisk.add(new AgentTypeData(
                            e.getDay(),
                            e.getAgentType() != null ? e.getAgentType() : "",
                            e.getRiskTolerance() != null ? e.getRiskTolerance() : 0));
                }
            }
        }
        return result;
    }

    private String resolveDbPath(String dbFileName) {
        File file = new File(dbFileName);
        if (file.exists()) return file.getAbsolutePath();
        File outFile = new File("output", dbFileName);
        return outFile.getAbsolutePath();
    }
}
