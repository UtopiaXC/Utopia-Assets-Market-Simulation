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

/**
 * Macro statistics service - uses MyBatis mappers
 */
@Service
public class MacroAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    public MacroStatsDTO getMacroStats(String dbFile) {
        MacroStatsDTO result = new MacroStatsDTO();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            AgentAssetDailyMapper agentMapper = session.getMapper(AgentAssetDailyMapper.class);
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);

            // Population history
            List<AgentAssetDailyEntity> activeCount = agentMapper.selectActiveCountByDay();
            result.populationHistory = new ArrayList<>();
            for (AgentAssetDailyEntity e : activeCount) {
                result.populationHistory.add(new PopulationData(
                        e.getDay(), e.getActiveAgents() != null ? e.getActiveAgents() : 0));
            }

            // Wealth history - combine market and agent data
            List<MarketDailyEntity> marketData = marketMapper.selectAll();
            List<AgentAssetDailyEntity> wealthData = agentMapper.selectWealthAggregateByDay();
            Map<Integer, AgentAssetDailyEntity> wealthMap = new HashMap<>();
            for (AgentAssetDailyEntity e : wealthData) {
                wealthMap.put(e.getDay(), e);
            }
            result.wealthHistory = new ArrayList<>();
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

            // Agent type assets
            List<AgentAssetDailyEntity> typeAssets = agentMapper.selectAssetsByTypeAndDay();
            result.agentTypeAssets = new ArrayList<>();
            for (AgentAssetDailyEntity e : typeAssets) {
                result.agentTypeAssets.add(new AgentTypeData(
                        e.getDay(),
                        e.getAgentType() != null ? e.getAgentType() : "",
                        e.getTotalAssets() != null ? e.getTotalAssets() : 0));
            }

            // Agent type risk
            List<AgentAssetDailyEntity> typeRisk = agentMapper.selectAvgRiskByTypeAndDay();
            result.agentTypeRisk = new ArrayList<>();
            for (AgentAssetDailyEntity e : typeRisk) {
                result.agentTypeRisk.add(new AgentTypeData(
                        e.getDay(),
                        e.getAgentType() != null ? e.getAgentType() : "",
                        e.getRiskTolerance() != null ? e.getRiskTolerance() : 0));
            }
        }
        return result;
    }
}
