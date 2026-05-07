package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentAssetDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentAssetDailyMapper {

    @Insert("INSERT OR REPLACE INTO agent_asset_daily (day, agent_id, cash, reserved_cash, " +
            "private_savings, stock_value, total_assets, risk_tolerance, is_active) " +
            "VALUES (#{day}, #{agentId}, #{cash}, #{reservedCash}, " +
            "#{privateSavings}, #{stockValue}, #{totalAssets}, #{riskTolerance}, #{isActive})")
    void insert(AgentAssetDailyEntity entity);

    @Select("SELECT a.*, ag.agent_type FROM agent_asset_daily a " +
            "JOIN agent ag ON a.agent_id = ag.id " +
            "WHERE a.day = #{day} ORDER BY a.agent_id")
    List<AgentAssetDailyEntity> selectByDay(int day);

    @Select("SELECT a.*, ag.agent_type FROM agent_asset_daily a " +
            "JOIN agent ag ON a.agent_id = ag.id " +
            "WHERE a.agent_id = #{agentId} ORDER BY a.day")
    List<AgentAssetDailyEntity> selectByAgentId(int agentId);

    @Select("SELECT a.*, ag.agent_type FROM agent_asset_daily a " +
            "JOIN agent ag ON a.agent_id = ag.id " +
            "WHERE a.agent_id = #{agentId} AND a.day = #{day}")
    AgentAssetDailyEntity selectByAgentIdAndDay(@Param("agentId") int agentId, @Param("day") int day);

    @Select("SELECT a.day, COUNT(*) as active_agents FROM agent_asset_daily a " +
            "WHERE a.is_active = 1 GROUP BY a.day ORDER BY a.day")
    List<AgentAssetDailyEntity> selectActiveCountByDay();

    @Select("SELECT a.day, ag.agent_type, SUM(a.total_assets) as total_assets " +
            "FROM agent_asset_daily a JOIN agent ag ON a.agent_id = ag.id " +
            "GROUP BY a.day, ag.agent_type ORDER BY a.day, ag.agent_type")
    List<AgentAssetDailyEntity> selectAssetsByTypeAndDay();

    @Select("SELECT a.day, ag.agent_type, AVG(a.risk_tolerance) as risk_tolerance " +
            "FROM agent_asset_daily a JOIN agent ag ON a.agent_id = ag.id " +
            "WHERE a.is_active = 1 " +
            "GROUP BY a.day, ag.agent_type ORDER BY a.day, ag.agent_type")
    List<AgentAssetDailyEntity> selectAvgRiskByTypeAndDay();

    @Select("SELECT a.day, SUM(a.private_savings) as private_savings, SUM(a.total_assets) as total_assets " +
            "FROM agent_asset_daily a GROUP BY a.day ORDER BY a.day")
    List<AgentAssetDailyEntity> selectWealthAggregateByDay();
}
