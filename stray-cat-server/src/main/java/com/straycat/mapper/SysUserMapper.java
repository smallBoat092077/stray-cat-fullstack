package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Update("UPDATE sys_user SET coin_balance = coin_balance + #{delta} WHERE id = #{userId}")
    int addCoin(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 信用积分累加，走 SQL 自增而不是先查后改，避免并发丢分；历史 NULL 按 100 起算
     */
    @Update("UPDATE sys_user SET credit_score = IFNULL(credit_score, 100) + #{delta} WHERE id = #{userId}")
    int addCredit(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 余额充足才扣减，靠 WHERE 条件而非先查后改来避免并发超扣；返回 0 表示余额不足
     */
    @Update("UPDATE sys_user SET coin_balance = coin_balance - #{delta} " +
            "WHERE id = #{userId} AND coin_balance >= #{delta}")
    int trySpendCoin(@Param("userId") Long userId, @Param("delta") int delta);
}
