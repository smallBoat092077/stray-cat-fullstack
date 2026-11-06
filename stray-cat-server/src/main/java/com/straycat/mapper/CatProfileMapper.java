package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.CatProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CatProfileMapper extends BaseMapper<CatProfile> {

    @Update("UPDATE cat_profile SET coin_total = coin_total + #{delta} WHERE id = #{catId}")
    int addCoinTotal(@Param("catId") Long catId, @Param("delta") int delta);

    @Update("UPDATE cat_profile SET view_count = view_count + 1 WHERE id = #{catId}")
    int addViewCount(@Param("catId") Long catId);

    @Update("UPDATE cat_profile SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{catId}")
    int addLikeCount(@Param("catId") Long catId, @Param("delta") int delta);

    @Update("UPDATE cat_profile SET comment_count = GREATEST(comment_count + #{delta}, 0) WHERE id = #{catId}")
    int addCommentCount(@Param("catId") Long catId, @Param("delta") int delta);
}
