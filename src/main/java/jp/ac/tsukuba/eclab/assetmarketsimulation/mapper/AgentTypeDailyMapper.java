package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentTypeDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentTypeDailyMapper {

    @Insert("INSERT OR REPLACE INTO agent_type_daily (day, agent_type, total_assets, avg_risk_tolerance, " +
            "active_count, total_count, cash, stock_value, private_savings) " +
            "VALUES (#{day}, #{agentType}, #{totalAssets}, #{avgRiskTolerance}, " +
            "#{activeCount}, #{totalCount}, #{cash}, #{stockValue}, #{privateSavings})")
    void insert(AgentTypeDailyEntity entity);

    @Select("SELECT * FROM agent_type_daily ORDER BY day, agent_type")
    List<AgentTypeDailyEntity> selectAll();

    @Select("SELECT * FROM agent_type_daily WHERE agent_type = #{agentType} ORDER BY day")
    List<AgentTypeDailyEntity> selectByAgentType(@Param("agentType") String agentType);

    @Select("SELECT COUNT(*) FROM agent_type_daily")
    int count();
}
