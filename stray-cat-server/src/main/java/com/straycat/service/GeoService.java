package com.straycat.service;

import com.straycat.vo.GeoResultVO;

/**
 * 坐标 -> 行政区（逆地理编码，后端代理腾讯位置服务，区域划分需求 R3/D2/D5）。
 * 实现恒不抛异常：未配置 key 返回 geoConfigured=false，网络/解析失败返回 success=false。
 */
public interface GeoService {

    GeoResultVO reverse(double latitude, double longitude);
}
