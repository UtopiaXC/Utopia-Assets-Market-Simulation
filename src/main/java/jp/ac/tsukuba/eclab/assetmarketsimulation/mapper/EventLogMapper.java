package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.EventLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface EventLogMapper {

    @Insert("INSERT OR REPLACE INTO event_log (event_id, day, event_type, source, parameters_json, description) " +
            "VALUES (#{eventId}, #{day}, #{eventType}, #{source}, #{parametersJson}, #{description})")
    void insert(EventLogEntity event);

    @Select("SELECT * FROM event_log ORDER BY day")
    List<EventLogEntity> selectAll();

    @Select("SELECT * FROM event_log WHERE day = #{day}")
    List<EventLogEntity> selectByDay(int day);

    @Select("SELECT * FROM event_log WHERE event_type = #{eventType}")
    List<EventLogEntity> selectByType(String eventType);
}
