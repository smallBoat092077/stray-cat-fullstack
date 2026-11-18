package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全事件审计：改密 / 锁定 / 解锁 / 邮箱变更等，detail 绝不记录密码与验证码。
 */
@Data
@TableName("user_security_event")
public class UserSecurityEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 冗余账号，账号被注销后仍可追溯 */
    private String username;

    /**
     * PWD_CHANGE / TEMP_LOCK / PERMANENT_LOCK / ADMIN_UNLOCK / EMAIL_CODE_SEND /
     * EMAIL_BIND / EMAIL_UNBIND / ADMIN_PWD_RESET
     */
    private String eventType;

    private String detail;

    /** 来源 IP */
    private String ip;

    private LocalDateTime createdAt;
}
