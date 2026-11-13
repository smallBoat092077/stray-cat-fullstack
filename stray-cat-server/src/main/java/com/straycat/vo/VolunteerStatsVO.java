package com.straycat.vo;

import lombok.Data;

/**
 * 志愿者等级面板：当前等级、服务时长、还差多少次升级
 */
@Data
public class VolunteerStatsVO {

    private Long userId;

    private String nickname;

    private Integer level;

    private String levelName;

    /**
     * 审核通过的打卡次数
     */
    private Integer completedCount;

    /**
     * 服务时长（小时），每次打卡 0.5 小时
     */
    private Double serviceHours;

    private Integer creditScore;

    /**
     * 下一级名称与门槛，满级时为空
     */
    private String nextLevelName;

    private Integer nextLevelCompleted;

    /**
     * 还差几次到下一级，满级为 0
     */
    private Integer remainToNext;

    /**
     * 当前等级内的进度百分比 0~100
     */
    private Integer progressPercent;

    /**
     * 升到下一级能拿到的猫粮奖励，满级为 0
     */
    private Integer nextReward;
}
