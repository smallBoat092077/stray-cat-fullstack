package com.straycat.service;

import com.straycat.vo.GeoRouteVO;

public interface GeoRouteService {

    /**
     * 从用户当前位置到某只猫的路线。腾讯接口不可用或未配 key 时退化为直线，不抛异常。
     */
    GeoRouteVO routeToCat(Long catId, Double fromLatitude, Double fromLongitude);
}
