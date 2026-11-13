package com.straycat.vo;

import lombok.Data;

/**
 * 志愿者排行榜的一行。昵称做脱敏展示，不暴露完整昵称
 */
@Data
public class VolunteerRankVO {

    private Integer rank;

    private Long userId;

    private String nickname;

    private Integer level;

    private String levelName;

    private Integer completedCount;

    private Double serviceHours;
}
