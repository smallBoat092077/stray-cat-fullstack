package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 共同领养人：主申请人邀请其他人一起承担这只猫的照顾责任。
 * 状态流转：0 待确认 → 1 已同意 / 2 已拒绝；被拒绝后可以再次邀请（复用同一行）。
 */
@Data
@TableName("adoption_co_adopter")
public class AdoptionCoAdopter {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private Long catId;

    /**
     * 发起邀请的主申请人
     */
    private Long inviterId;

    /**
     * 被邀请人
     */
    private Long userId;

    /**
     * 状态: 0待确认 1已同意 2已拒绝
     */
    private Integer status;

    private String message;

    private LocalDateTime createdAt;

    private LocalDateTime respondedAt;

    @TableField(exist = false)
    private String catName;

    @TableField(exist = false)
    private String inviterName;

    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String userAvatar;

    @TableField(exist = false)
    private Integer applicationStatus;
}
