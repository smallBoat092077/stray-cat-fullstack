package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.WikiQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiQuestionMapper extends BaseMapper<WikiQuestion> {
}
