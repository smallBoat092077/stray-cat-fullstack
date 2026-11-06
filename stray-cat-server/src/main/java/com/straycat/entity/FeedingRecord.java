package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("feeding_record")
public class FeedingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long catId;

    private Long volunteerId;

    private LocalDateTime checkInTime;

    private BigDecimal checkInLatitude;

    private BigDecimal checkInLongitude;

    private Integer distance;

    private String foodUsed;

    private String catCondition;
    /**
     * 打卡照片，逗号分隔的 /uploads/ 相对路径
     */
    private String images;

    /**
     * 审核状态: 0待审核 1已通过 2已拒绝
     */
    private Integer status;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
