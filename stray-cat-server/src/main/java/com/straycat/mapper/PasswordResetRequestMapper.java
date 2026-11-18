package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.PasswordResetRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PasswordResetRequestMapper extends BaseMapper<PasswordResetRequest> {
}
