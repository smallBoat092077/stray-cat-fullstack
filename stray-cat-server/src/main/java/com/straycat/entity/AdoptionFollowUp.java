package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("adoption_follow_up")
public class AdoptionFollowUp {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private Long catId;

    private Long adopterId;

    private String catCondition;

    private String livingEnvironment;

    private String healthStatus;

    private String images;

    private Integer followUpType;

    /**
     * 0待回访 1已回访 2异常
     */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;
}
