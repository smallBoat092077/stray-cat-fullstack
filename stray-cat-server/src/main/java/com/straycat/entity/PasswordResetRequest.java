package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小程序「忘记密码」找回申请：免登提交，管理员人工核实后线下电话转告临时密码。
 */
@Data
@TableName("password_reset_request")
public class PasswordResetRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户填写的账号 */
    private String username;

    /** 用户填写的昵称（核实口径之一） */
    private String nickname;

    /** 预留手机号，管理员线下联系用 */
    private String phone;

    /** 情况说明 */
    private String reason;

    /** 0待处理 1已重置 2已忽略 */
    private Integer status;

    /** 处理管理员 id */
    private Long operatorId;

    /** 处理管理员账号名（冗余） */
    private String operatorName;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    /** 提交来源 IP */
    private String ip;
}
