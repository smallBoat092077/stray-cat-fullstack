package com.straycat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * cat_feature 表由 Python 识别服务自建自管，Java 侧只读：不建表、不改表、不写入。
 * 别名保持 feature_id / cat_id / image_url / created_at，供后台「花纹样本」列表直接用。
 */
@Mapper
public interface CatFeatureMapper {

    @Select("<script>" +
            "SELECT id AS feature_id, cat_id, image_url, created_at FROM cat_feature " +
            "<if test='catId != null'>WHERE cat_id = #{catId} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<Map<String, Object>> samples(@Param("catId") Long catId);

    @Select("SELECT COUNT(*) FROM cat_feature")
    int countAll();
}
