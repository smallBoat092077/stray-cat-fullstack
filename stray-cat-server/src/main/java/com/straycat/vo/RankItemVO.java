package com.straycat.vo;

import lombok.Data;

/**
 * 三榜的一行。昵称按 VolunteerLevelServiceImpl.mask() 同逻辑脱敏。
 */
@Data
public class RankItemVO {

    private Integer rank;

    private Long userId;

    private String nickname;

    private String levelName;

    private Long value;

    /** 次投喂 / 份猫粮 / 天 */
    private String unit;
}
