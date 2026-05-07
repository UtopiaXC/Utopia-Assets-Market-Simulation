package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.SimulationMeta;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

public interface SimulationMetaMapper {

    @Insert("INSERT INTO simulation_meta (seed, start_time, scenario_name, num_stocks, num_agents, simulation_days, steps_per_day, config_json) " +
            "VALUES (#{seed}, #{startTime}, #{scenarioName}, #{numStocks}, #{numAgents}, #{simulationDays}, #{stepsPerDay}, #{configJson})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SimulationMeta meta);

    @Select("SELECT * FROM simulation_meta WHERE id = #{id}")
    SimulationMeta selectById(int id);

    @Select("SELECT * FROM simulation_meta LIMIT 1")
    SimulationMeta selectFirst();
}
