package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.SectorDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SectorDailyMapper {

    @Insert("INSERT OR REPLACE INTO sector_daily (day, sector_id, sector_name, total_market_cap, avg_pe, total_volume, total_turnover, stock_count) " +
            "VALUES (#{day}, #{sectorId}, #{sectorName}, #{totalMarketCap}, #{avgPe}, #{totalVolume}, #{totalTurnover}, #{stockCount})")
    void insert(SectorDailyEntity entity);

    @Select("SELECT * FROM sector_daily ORDER BY day, sector_id")
    List<SectorDailyEntity> selectAll();

    @Select("SELECT * FROM sector_daily WHERE sector_id = #{sectorId} ORDER BY day")
    List<SectorDailyEntity> selectBySectorId(@Param("sectorId") int sectorId);

    @Select("SELECT * FROM sector_daily WHERE sector_name = #{sectorName} ORDER BY day")
    List<SectorDailyEntity> selectBySectorName(@Param("sectorName") String sectorName);

    @Select("SELECT COUNT(*) FROM sector_daily")
    int count();
}
