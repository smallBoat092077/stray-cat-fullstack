package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销活动。code 与 share_link.campaign 对齐：
 * 运营为活动生成带参小程序码（scene=s=短码），用户扫码落地 pages/campaign/index，
 * 传播与转化数据由 share_link / share_visit 自动记录。
 */
@Data
@TableName("campaign")
public class Campaign {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动代号，与 share_link.campaign 一致，全站唯一定位一场活动 */
    private String code;

    /** 活动名 */
    private String name;

    /** 活动介绍 */
    private String description;

    /** 封面图相对路径 */
    private String coverUrl;

    /** 0 下线 1 上线 */
    private Integer status;

    /** 开始时间，NULL 不限 */
    private LocalDateTime startTime;

    /** 结束时间，NULL 不限 */
    private LocalDateTime endTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
