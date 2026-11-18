package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.Region;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RegionMapper extends BaseMapper<Region> {

    /**
     * 批量幂等写入：code 相同的行跳过（直辖市省/市同码冲突去重）。
     */
    @Insert({
        "<script>",
        "INSERT IGNORE INTO region (code, name, parent_code, level, sort) VALUES ",
        "<foreach collection='rows' item='r' separator=','>",
        "(#{r.code}, #{r.name}, #{r.parentCode}, #{r.level}, #{r.sort})",
        "</foreach>",
        "</script>"
    })
    int insertIgnoreBatch(@Param("rows") List<Region> rows);
}
