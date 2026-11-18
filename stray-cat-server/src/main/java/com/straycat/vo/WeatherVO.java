package com.straycat.vo;

import lombok.Data;

import java.util.List;

/**
 * 首页天气卡片的归一化对外视图（天气卡片功能设计 §3.1）。
 * 只暴露展示所需字段，与腾讯 /ws/weather/v1/ 原始字段解耦，便于解析口径调整。
 */
@Data
public class WeatherVO {

    /** 区域显示名，如「湖北省武汉市洪山区」；取不到时为空 */
    private String cityName;

    /** 实况 */
    private Now now;

    /** 未来最多 3 天预报 */
    private List<FutureDay> future;

    /** 数据更新时间 */
    private String updateTime;

    @Data
    public static class Now {
        /** 天气现象，如「多云」 */
        private String weather;
        /** 温度（摄氏度） */
        private Integer temperature;
        /** 湿度（%），可能为 null */
        private Integer humidity;
        /** 风向，如「东南风」 */
        private String windDirection;
        /** 风力，如「2级」 */
        private String windPower;
    }

    @Data
    public static class FutureDay {
        /** 日期，如「2026-11-17」或「11-17」 */
        private String date;
        /** 天气现象 */
        private String weather;
        /** 最高温 */
        private Integer high;
        /** 最低温 */
        private Integer low;
    }
}
