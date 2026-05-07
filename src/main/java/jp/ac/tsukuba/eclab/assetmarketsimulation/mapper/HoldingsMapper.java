package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.HoldingsDeltaEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.HoldingsSnapshotEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface HoldingsMapper {

    @Insert("INSERT OR REPLACE INTO holdings_snapshot (snapshot_day, agent_id, holdings_json) " +
            "VALUES (#{snapshotDay}, #{agentId}, #{holdingsJson})")
    void insertSnapshot(HoldingsSnapshotEntity snapshot);

    @Insert("INSERT OR REPLACE INTO holdings_delta (day, agent_id, stock_id, quantity_change) " +
            "VALUES (#{day}, #{agentId}, #{stockId}, #{quantityChange})")
    void insertDelta(HoldingsDeltaEntity delta);

    @Select("SELECT * FROM holdings_snapshot WHERE agent_id = #{agentId} " +
            "AND snapshot_day <= #{day} ORDER BY snapshot_day DESC LIMIT 1")
    HoldingsSnapshotEntity selectLatestSnapshot(@Param("agentId") int agentId, @Param("day") int day);

    @Select("SELECT * FROM holdings_delta WHERE agent_id = #{agentId} " +
            "AND day > #{afterDay} AND day <= #{untilDay} ORDER BY day")
    List<HoldingsDeltaEntity> selectDeltasInRange(@Param("agentId") int agentId,
                                                   @Param("afterDay") int afterDay,
                                                   @Param("untilDay") int untilDay);

    @Select("SELECT * FROM holdings_snapshot WHERE snapshot_day = #{day}")
    List<HoldingsSnapshotEntity> selectSnapshotsByDay(int day);

    /** Get all agents holding a specific stock on a snapshot day */
    @Select("SELECT hs.* FROM holdings_snapshot hs " +
            "WHERE hs.snapshot_day = (SELECT MAX(snapshot_day) FROM holdings_snapshot WHERE snapshot_day <= #{day}) " +
            "AND hs.holdings_json LIKE '%\"' || #{stockId} || '\"%'")
    List<HoldingsSnapshotEntity> selectSnapshotsByStock(@Param("stockId") int stockId, @Param("day") int day);
}
