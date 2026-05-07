package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.AgentEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentMapper {

    @Insert("INSERT OR IGNORE INTO agent (id, agent_type, initial_cash, max_stocks, initial_risk_tolerance) " +
            "VALUES (#{id}, #{agentType}, #{initialCash}, #{maxStocks}, #{initialRiskTolerance})")
    void insert(AgentEntity agent);

    @Select("SELECT * FROM agent ORDER BY id")
    List<AgentEntity> selectAll();

    @Select("SELECT * FROM agent WHERE id = #{id}")
    AgentEntity selectById(int id);

    @Select("SELECT * FROM agent WHERE agent_type = #{agentType}")
    List<AgentEntity> selectByType(String agentType);

    @Select("SELECT COUNT(*) FROM agent")
    int countAll();
}
