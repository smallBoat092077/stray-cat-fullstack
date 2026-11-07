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
            "ORDER BY t.created_at DESC")
    Page<FeedingTask> selectTaskPage(Page<FeedingTask> page, @Param("keyword") String keyword,
                                     @Param("status") Integer status, @Param("sponsorId") Long sponsorId);
}
