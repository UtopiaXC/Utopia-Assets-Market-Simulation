package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.IpoEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.IpoSubscriptionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IpoMapper {

    @Insert("INSERT OR REPLACE INTO ipo (stock_id, ipo_price, available_shares, demand_shares, oversubscription_ratio) " +
            "VALUES (#{stockId}, #{ipoPrice}, #{availableShares}, #{demandShares}, #{oversubscriptionRatio})")
    void insertIpo(IpoEntity ipo);

    @Insert("INSERT OR REPLACE INTO ipo_subscription (stock_id, agent_id, demand_shares, allocated_shares) " +
            "VALUES (#{stockId}, #{agentId}, #{demandShares}, #{allocatedShares})")
    void insertSubscription(IpoSubscriptionEntity subscription);

    @Select("SELECT * FROM ipo ORDER BY stock_id")
    List<IpoEntity> selectAllIpo();

    @Select("SELECT * FROM ipo_subscription WHERE stock_id = #{stockId}")
    List<IpoSubscriptionEntity> selectSubscriptionsByStock(int stockId);
}
