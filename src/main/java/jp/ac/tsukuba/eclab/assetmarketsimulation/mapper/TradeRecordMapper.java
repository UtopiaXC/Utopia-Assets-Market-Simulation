package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.TradeRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TradeRecordMapper {

    @Insert("INSERT INTO trade_record (day, stock_id, buyer_id, seller_id, price, quantity) " +
            "VALUES (#{day}, #{stockId}, #{buyerId}, #{sellerId}, #{price}, #{quantity})")
    void insert(TradeRecordEntity record);

    @Select("SELECT t.*, s.stock_code, ab.agent_type as buyer_type, as2.agent_type as seller_type " +
            "FROM trade_record t " +
            "JOIN stock s ON t.stock_id = s.id " +
            "JOIN agent ab ON t.buyer_id = ab.id " +
            "JOIN agent as2 ON t.seller_id = as2.id " +
            "WHERE t.day = #{day} ORDER BY t.rowid")
    List<TradeRecordEntity> selectByDay(int day);

    @Select("SELECT t.*, s.stock_code, ab.agent_type as buyer_type, as2.agent_type as seller_type " +
            "FROM trade_record t " +
            "JOIN stock s ON t.stock_id = s.id " +
            "JOIN agent ab ON t.buyer_id = ab.id " +
            "JOIN agent as2 ON t.seller_id = as2.id " +
            "WHERE t.stock_id = #{stockId} AND t.day = #{day} ORDER BY t.rowid")
    List<TradeRecordEntity> selectByStockAndDay(@Param("stockId") int stockId, @Param("day") int day);

    @Select("SELECT t.*, s.stock_code, ab.agent_type as buyer_type, as2.agent_type as seller_type " +
            "FROM trade_record t " +
            "JOIN stock s ON t.stock_id = s.id " +
            "JOIN agent ab ON t.buyer_id = ab.id " +
            "JOIN agent as2 ON t.seller_id = as2.id " +
            "WHERE (t.buyer_id = #{agentId} OR t.seller_id = #{agentId}) AND t.day = #{day} " +
            "ORDER BY t.rowid")
    List<TradeRecordEntity> selectByAgentAndDay(@Param("agentId") int agentId, @Param("day") int day);

    @Select("SELECT t.day, COUNT(*) as quantity, SUM(t.price * t.quantity) as price " +
            "FROM trade_record t WHERE t.stock_id = #{stockId} " +
            "GROUP BY t.day ORDER BY t.day")
    List<TradeRecordEntity> selectDailyTradeStats(@Param("stockId") int stockId);

    @Select("SELECT t.day, COUNT(*) as quantity, SUM(t.price * t.quantity) as price " +
            "FROM trade_record t GROUP BY t.day ORDER BY t.day")
    List<TradeRecordEntity> selectMarketDailyTradeStats();
}
