package com.straycat.vo;

import lombok.Data;

/**
 * 我的名次。未登录或数值为 0 时接口返回 null（前端显示「登录后查看你的名次」）。
 */
@Data
public class MyRankVO {

    private String type;

    private String period;

    private Integer rank;

    private Long value;

    /** 次投喂 / 份猫粮 / 天 */
    private String unit;

    private Long userId;
}
