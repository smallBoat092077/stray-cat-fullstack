package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("adoption_application")
public class AdoptionApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long catId;

    private Long applicantId;

    /**
     * 住房类型: 0整租 1合租 2自有 3宿舍
     */
    private Integer housingType;

    private java.math.BigDecimal housingArea;

    private Integer hasOtherPets;

    private String catExperience;

    private String adoptionReason;

    private Integer familyAgree;

    private String workSchedule;

    /**
     * 联系手机号，审核时要靠它联系申请人确认
     */
    private String contactPhone;

    /**
     * 微信号，选填
     */
    private String contactWechat;

    /**
     * 状态: 0待审核 1审核中 2已通过 3已拒绝 4已签约 5已完成
     */
    private Integer status;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
