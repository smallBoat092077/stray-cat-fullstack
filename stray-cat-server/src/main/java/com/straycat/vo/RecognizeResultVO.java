package com.straycat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 打卡识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognizeResultVO {

    /**
     * 是否识别成功
     */
    private Boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 匹配结果列表
     */
    private List<MatchInfo> matches;

    /**
     * 匹配信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchInfo {
        /**
         * 猫咪 ID
         */
        private Long catId;

        /**
         * 相似度 (0-1)
         */
        private Double similarity;

        /**
         * 猫咪名称
         */
        private String catName;

        /**
         * 猫咪头像
         */
        private String avatar;

        /**
         * 原位置（经纬度）
         */
        private Double originalLng;
        private Double originalLat;

        /**
         * 是否需要人工确认（相似度 70-85%）
         */
        private Boolean needsManualConfirm;
    }
}
