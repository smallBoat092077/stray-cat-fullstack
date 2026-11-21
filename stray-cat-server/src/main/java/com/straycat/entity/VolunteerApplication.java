package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 志愿者申请。状态: 0待审核 1已通过 2已拒绝。
 */
@Data
@TableName("volunteer_application")
public class VolunteerApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicantId;

    private String realName;

    private String contact;

    private String region;

    private String serviceTime;

    private String reason;

    private Integer status;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 申请人昵称，列表展示用，不落库 */
    @TableField(exist = false)
    private String applicantNickname;
}
