package jp.ac.tsukuba.eclab.assetmarketsimulation.mapper;

import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.SectorEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SectorMapper {

    @Insert("INSERT OR IGNORE INTO sector (id, name, display_name) VALUES (#{id}, #{name}, #{displayName})")
    void insert(SectorEntity sector);

    @Select("SELECT * FROM sector ORDER BY id")
    List<SectorEntity> selectAll();

    @Select("SELECT * FROM sector WHERE id = #{id}")
    SectorEntity selectById(int id);

    @Select("SELECT * FROM sector WHERE name = #{name}")
    SectorEntity selectByName(String name);
}
