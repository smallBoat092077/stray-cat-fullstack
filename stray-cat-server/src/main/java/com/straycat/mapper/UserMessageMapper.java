package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {

    /**
     * 只置未读为已读，一次 UPDATE 完成「全部已读」，不需要先查再逐条改
     */
    @Update("UPDATE user_message SET read_flag = 1 WHERE user_id = #{userId} AND read_flag = 0")
    int markAllRead(@Param("userId") Long userId);
}
