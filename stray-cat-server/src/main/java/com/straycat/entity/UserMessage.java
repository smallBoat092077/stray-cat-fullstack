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

    private LocalDateTime createdAt;
}
