package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StockDailyMapper {

    @Insert("INSERT OR REPLACE INTO stock_daily (day, stock_id, open, high, low, close, volume, turnover, " +
            "pb_ratio, pe_ttm, pe_dynamic, pe_static, eps, net_assets, " +
            "total_market_cap, liquid_market_cap, turnover_rate, amplitude, high_52w, low_52w) " +
            "VALUES (#{day}, #{stockId}, #{open}, #{high}, #{low}, #{close}, #{volume}, #{turnover}, " +
            "#{pbRatio}, #{peTtm}, #{peDynamic}, #{peStatic}, #{eps}, #{netAssets}, " +
            "#{totalMarketCap}, #{liquidMarketCap}, #{turnoverRate}, #{amplitude}, #{high52w}, #{low52w})")
    void insert(StockDailyEntity entity);

    @Select("SELECT sd.*, s.stock_code, sec.name as sector_name " +
            "FROM stock_daily sd " +
            "JOIN stock s ON sd.stock_id = s.id " +
            "JOIN sector sec ON s.sector_id = sec.id " +
            "WHERE sd.stock_id = #{stockId} ORDER BY sd.day")
    List<StockDailyEntity> selectByStockId(int stockId);

    @Select("SELECT sd.*, s.stock_code, sec.name as sector_name " +
            "FROM stock_daily sd " +
            "JOIN stock s ON sd.stock_id = s.id " +
            "JOIN sector sec ON s.sector_id = sec.id " +
            "WHERE sd.day = #{day} ORDER BY sd.stock_id")
    List<StockDailyEntity> selectByDay(int day);

    @Select("SELECT sd.*, s.stock_code, sec.name as sector_name " +
            "FROM stock_daily sd " +
            "JOIN stock s ON sd.stock_id = s.id " +
            "JOIN sector sec ON s.sector_id = sec.id " +
            "WHERE sd.stock_id = #{stockId} AND sd.day = #{day}")
    StockDailyEntity selectByStockIdAndDay(@Param("stockId") int stockId, @Param("day") int day);

    @Select("SELECT sd.*, s.stock_code, sec.name as sector_name " +
            "FROM stock_daily sd " +
            "JOIN stock s ON sd.stock_id = s.id " +
            "JOIN sector sec ON s.sector_id = sec.id " +
            "WHERE sd.day = #{day} ORDER BY sd.turnover DESC LIMIT #{limit}")
    List<StockDailyEntity> selectTopByTurnover(@Param("day") int day, @Param("limit") int limit);

    @Select("SELECT sd.day, sec.name as sector_name, " +
            "SUM(sd.total_market_cap) as total_market_cap, " +
            "AVG(sd.pe_ttm) as pe_ttm " +
            "FROM stock_daily sd " +
            "JOIN stock s ON sd.stock_id = s.id " +
            "JOIN sector sec ON s.sector_id = sec.id " +
            "GROUP BY sd.day, sec.name ORDER BY sd.day, sec.name")
    List<StockDailyEntity> selectSectorAggregates();

    @Select("SELECT sd.*, s.stock_code, sec.name as sector_name " +
            "FROM stock_daily sd " +
            "JOIN stock s ON sd.stock_id = s.id " +
            "JOIN sector sec ON s.sector_id = sec.id " +
            "WHERE sec.name = #{sectorName} AND sd.day = #{day} " +
            "ORDER BY sd.total_market_cap DESC")
    List<StockDailyEntity> selectBySectorAndDay(@Param("sectorName") String sectorName, @Param("day") int day);
}
