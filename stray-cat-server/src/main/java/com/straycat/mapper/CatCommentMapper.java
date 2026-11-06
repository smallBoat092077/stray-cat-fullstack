package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.CatComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CatCommentMapper extends BaseMapper<CatComment> {
}
