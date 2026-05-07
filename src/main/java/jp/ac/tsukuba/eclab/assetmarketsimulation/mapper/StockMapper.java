package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StockMapper {

    @Insert("INSERT OR IGNORE INTO stock (id, stock_code, sector_id, ipo_price, total_shares, liquid_shares, " +
            "initial_net_assets, initial_eps, earnings_growth, beta) " +
            "VALUES (#{id}, #{stockCode}, #{sectorId}, #{ipoPrice}, #{totalShares}, #{liquidShares}, " +
            "#{initialNetAssets}, #{initialEps}, #{earningsGrowth}, #{beta})")
    void insert(StockEntity stock);

    @Select("SELECT s.*, sec.name as sector_name FROM stock s JOIN sector sec ON s.sector_id = sec.id ORDER BY s.id")
    List<StockEntity> selectAll();

    @Select("SELECT s.*, sec.name as sector_name FROM stock s JOIN sector sec ON s.sector_id = sec.id WHERE s.id = #{id}")
    StockEntity selectById(int id);

    @Select("SELECT s.*, sec.name as sector_name FROM stock s JOIN sector sec ON s.sector_id = sec.id WHERE s.stock_code = #{stockCode}")
    StockEntity selectByCode(String stockCode);

    @Select("SELECT s.*, sec.name as sector_name FROM stock s JOIN sector sec ON s.sector_id = sec.id WHERE s.sector_id = #{sectorId}")
    List<StockEntity> selectBySector(int sectorId);
}
