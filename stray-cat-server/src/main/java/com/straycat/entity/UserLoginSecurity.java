package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录安全状态：按账号(username)维度一行，承载阶梯锁定的计数与阶段。
 *
 * <p>计数遵循「连续」语义：任一次密码验证成功、24 小时到期自动解锁、管理员解锁都会清零。
 * unlocked_at 除记录管理员解锁外，也用于标记「账号已经历过一次临时锁、进入三振周期」：
 * 第一次锁之前容忍 5 次失败，经历过解锁后只容忍 3 次，对应方案的「新周期累计第 3 次失败永久锁」。
 */
@Data
@TableName("user_login_security")
public class UserLoginSecurity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id（账号不存在时为空） */
    private Long userId;

    /** 规范化后的账号，唯一索引 */
    private String username;

    /** 当前周期连续失败次数 */
    private Integer failedCount;

    /** 0 正常 / 1 临时锁 / 2 永久锁 */
    private Integer lockStage;

    /** 临时锁到期时间；永久锁为 NULL */
    private LocalDateTime lockUntil;

    /** 本周期首次失败时间 */
    private LocalDateTime firstFailedAt;

    /** 本周期末次失败时间 */
    private LocalDateTime lastFailedAt;

    /** 最近一次解锁时间（管理员或 24 小时到期），同时标记已进入三振周期 */
    private LocalDateTime unlockedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
