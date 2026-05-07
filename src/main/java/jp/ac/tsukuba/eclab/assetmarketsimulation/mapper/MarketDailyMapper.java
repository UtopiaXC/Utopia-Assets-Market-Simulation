package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.MarketDailyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MarketDailyMapper {

    @Insert("INSERT OR REPLACE INTO market_daily (day, open, high, low, close, volume, turnover, " +
            "total_market_cap, amplitude, turnover_rate, social_wealth_pool, active_agents) " +
            "VALUES (#{day}, #{open}, #{high}, #{low}, #{close}, #{volume}, #{turnover}, " +
            "#{totalMarketCap}, #{amplitude}, #{turnoverRate}, #{socialWealthPool}, #{activeAgents})")
    void insert(MarketDailyEntity entity);

    @Select("SELECT * FROM market_daily ORDER BY day")
    List<MarketDailyEntity> selectAll();

    @Select("SELECT * FROM market_daily WHERE day = #{day}")
    MarketDailyEntity selectByDay(int day);

    @Select("SELECT * FROM market_daily WHERE day BETWEEN #{startDay} AND #{endDay} ORDER BY day")
    List<MarketDailyEntity> selectByRange(int startDay, int endDay);

    @Select("SELECT MAX(day) FROM market_daily")
    Integer selectMaxDay();
}
