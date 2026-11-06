package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("feeding_task")
public class FeedingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long catId;

    /**
     * 类型: 0日常投喂 1紧急投喂 2医疗救助
     */
    private Integer taskType;

    /**
     * 状态: 0待接单 1已接单 2已投喂待审核 3已完成 4已取消
     */
    private Integer status;

    private Long volunteerId;

    private Long sponsorId;

    /**
     * 由哪条打赏记录生成的任务，人工派单时为 null
     */
    private Long tipId;

    private BigDecimal amount;

    private String foodType;

    private Integer foodQuantity;

    private BigDecimal latitude;

    private BigDecimal longitude;

    /**
     * 打卡有效半径(米)
     */
    private Integer radius;

    private LocalDateTime deadline;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 关联猫咪的名字和地点，来自 cat_profile，feeding_task 表里没有这两列
     */
    @TableField(exist = false)
    private String catName;

    @TableField(exist = false)
    private String locationDesc;

    /**
     * 接单人的昵称和角色，来自 sys_user
     */
    @TableField(exist = false)
    private String volunteerName;

    @TableField(exist = false)
    private Integer volunteerRole;

    /**
     * 接单人历史已完成（status=3）的投喂任务数
     */
    @TableField(exist = false)
    private Integer volunteerDoneCount;
}
