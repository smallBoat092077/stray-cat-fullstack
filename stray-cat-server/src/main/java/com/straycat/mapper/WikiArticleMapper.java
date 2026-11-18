package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.WikiArticle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WikiArticleMapper extends BaseMapper<WikiArticle> {

    /** 文章阅读 +1，失败由 Service 吞掉 */
    @Update("UPDATE wiki_article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementView(@Param("id") Long id);
}
