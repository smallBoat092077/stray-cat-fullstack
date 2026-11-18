package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理操作日志：新增用户 / 重置密码 / 找回申请处理。detail 绝不记录密码明文。
 */
@Data
@TableName("admin_operation_log")
public class AdminOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员 id */
    private Long operatorId;

    /** 操作管理员账号名（冗余，防改名后审计断链） */
    private String operatorName;

    /** CREATE_USER / RESET_PASSWORD / RESET_REQUEST_HANDLE */
    private String action;

    /** 目标用户 id；找回申请场景记为申请 id */
    private Long targetUserId;

    /** 目标用户账号 */
    private String targetUsername;

    /** 备注（角色、处理动作等，不含密码） */
    private String detail;

    /** 操作来源 IP */
    private String ip;

    private LocalDateTime createdAt;
}
