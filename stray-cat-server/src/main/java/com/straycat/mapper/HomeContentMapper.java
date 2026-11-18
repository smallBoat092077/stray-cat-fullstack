package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.HomeContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HomeContentMapper extends BaseMapper<HomeContent> {

    /** 通告详情 pv / 热点点击统一自增，失败由 Service 吞掉不阻塞 */
    @Update("UPDATE home_content SET pv = pv + 1 WHERE id = #{id}")
    int incrementPv(@Param("id") Long id);
}
