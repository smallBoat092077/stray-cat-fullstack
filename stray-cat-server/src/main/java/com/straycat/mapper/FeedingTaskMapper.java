package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.FeedingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FeedingTaskMapper extends BaseMapper<FeedingTask> {

    @Select("SELECT t.* FROM feeding_task t " +
            "LEFT JOIN cat_profile c ON t.cat_id = c.id " +
            "WHERE (#{keyword} IS NULL OR #{keyword} = '' OR c.name LIKE CONCAT('%', #{keyword}, '%') OR c.location_desc LIKE CONCAT('%', #{keyword}, '%')) " +
            "AND (#{status} IS NULL OR t.status = #{status}) " +
            "AND (#{sponsorId} IS NULL OR t.sponsor_id = #{sponsorId}) " +
            // 区域过滤（区域划分需求 D7：投喂地点随猫，按猫的归属区域过滤）；不带参数 = 全部口径
            "AND (#{provinceCode} IS NULL OR #{provinceCode} = '' OR c.province_code = #{provinceCode}) " +
            "AND (#{cityCode} IS NULL OR #{cityCode} = '' OR c.city_code = #{cityCode}) " +
            "AND (#{districtCode} IS NULL OR #{districtCode} = '' OR c.district_code = #{districtCode}) " +
            "ORDER BY t.created_at DESC")
    Page<FeedingTask> selectTaskPage(Page<FeedingTask> page, @Param("keyword") String keyword,
                                     @Param("status") Integer status, @Param("sponsorId") Long sponsorId,
                                     @Param("provinceCode") String provinceCode,
                                     @Param("cityCode") String cityCode,
                                     @Param("districtCode") String districtCode);
}
