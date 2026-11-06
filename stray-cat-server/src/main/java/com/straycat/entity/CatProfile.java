package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cat_profile")
public class CatProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 性别: 0母 1公 2未知
     */
    private Integer gender;

    private String ageEstimate;

    private String breed;

    private String color;

    private String personality;

    /**
     * 健康状态: 0未检查 1已检查 2已绝育 3已疫苗 4已驱虫
     */
    private Integer healthStatus;

    /**
     * 生活状态: 0流浪中 1投喂中 2待领养 3已领养 4领养回访中
     */
    private Integer lifeStatus;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String locationDesc;

    private Long discovererId;

    private Long caregiverId;

    private Long adopterId;

    private String description;

    private String coverImage;

    private Integer viewCount;

    private Integer likeCount;

    /**
     * 评论数（含回复），由评论接口维护
     */
    private Integer commentCount;

    /**
     * 累计收到的猫粮
     */
    private Integer coinTotal;

    /**
     * 当前用户是否已点赞，仅详情接口回填，不落库
     */
    @TableField(exist = false)
    private Boolean liked;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
