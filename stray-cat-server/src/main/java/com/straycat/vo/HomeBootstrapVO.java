package com.straycat.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 首屏聚合响应（方案 5.1）。一次 setData 需要的公共内容 + 三榜默认月榜 Top5 + 模块开关。
 * 只由基本类型/字符串/List/Map 组成，便于整体 JSON 缓存与反序列化。
 */
@Data
public class HomeBootstrapVO {

    /** 热点轮播，最多 hotspot-limit 条 */
    private List<ContentItem> hotspots;

    /** 最新（置顶优先）通告 1 条，无则 null */
    private ContentItem notice;

    /** feeding / food / active 三榜默认月榜 */
    private Map<String, List<RankItemVO>> boards;

    /** hotspot / notice / leaderboard / lostCat / weather 灰度开关，可单独关闭任一模块 */
    private Map<String, Boolean> modules;

    /**
     * 热点卡 / 通告条的精简视图，字段与方案 5.1 bootstrap 示意一致。
     */
    @Data
    public static class ContentItem {

        private Long id;

        private String title;

        private String summary;

        private String coverUrl;

        private String tag;

        private String linkType;

        private String linkUrl;
    }
}
