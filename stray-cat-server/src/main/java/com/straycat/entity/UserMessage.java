package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** MessageType 枚举名 */
    private String type;

    private String title;

    private String content;

    /** 小程序跳转路径，落库时已展开业务 id */
    private String page;

    private Long bizId;

    private Integer readFlag;

    /**
     * 微信订阅消息的推送结果：0 仅站内（未配置模板或无需推送）、1 已推送、
     * 2 无订阅额度（用户没授权或额度已用完）、3 推送失败、4 未绑定微信。
     */
    private Integer pushStatus;

    /** 推送失败原因摘要（errcode + errmsg），仅排查用 */
    private String pushError;

    private LocalDateTime createdAt;
}
