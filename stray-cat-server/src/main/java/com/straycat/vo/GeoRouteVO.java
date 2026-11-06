package com.straycat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class GeoRouteVO {

    /**
     * tencent = 真实路网路线；straight = 两点直线（未配 key 或腾讯接口不可用时的退化结果）
     */
    private String source;

    private String mode;

    private Integer distanceMeters;

    private Integer durationSeconds;

    private List<Point> polyline;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {

        private Double latitude;

        private Double longitude;
    }
}
