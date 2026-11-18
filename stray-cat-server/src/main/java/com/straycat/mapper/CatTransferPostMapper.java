package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.CatTransferPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CatTransferPostMapper extends BaseMapper<CatTransferPost> {

    /** 详情浏览 +1，失败由 Service 吞掉 */
    @Update("UPDATE cat_transfer_post SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementView(@Param("id") Long id);

    /** 新的接猫申请 +1，失败由 Service 吞掉 */
    @Update("UPDATE cat_transfer_post SET apply_count = apply_count + 1 WHERE id = #{id}")
    int incrementApply(@Param("id") Long id);
}
