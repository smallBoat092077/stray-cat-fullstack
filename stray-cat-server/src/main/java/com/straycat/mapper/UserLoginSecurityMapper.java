package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.UserLoginSecurity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserLoginSecurityMapper extends BaseMapper<UserLoginSecurity> {

    /**
     * 并发安全：先 INSERT IGNORE 占位（唯一索引 uk_username 兜底），再对已有行加排他锁，
     * 保证同一账号的失败计数不会因并发而丢失。重复插入不影响已有行。
     */
    @Insert("INSERT IGNORE INTO user_login_security (username, user_id, failed_count, lock_stage, created_at, updated_at) " +
            "VALUES (#{username}, #{userId}, 0, 0, NOW(), NOW())")
    int insertIgnore(@Param("username") String username, @Param("userId") Long userId);

    @Select("SELECT * FROM user_login_security WHERE username = #{username} FOR UPDATE")
    UserLoginSecurity selectForUpdate(@Param("username") String username);
}
