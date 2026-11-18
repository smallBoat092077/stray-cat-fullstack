package com.straycat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 逆地理编码结果（区域划分需求 §2）。任一行政区解析不到时对应位为 null。
 * geoConfigured=false 表示后端未配置腾讯 key（降级：调用方按「无法定位区域」处理）。
 */
@Data
@Schema(description = "坐标 -> 行政区解析结果")
public class GeoResultVO {

    @Schema(description = "后端是否已配置腾讯位置服务")
    private boolean geoConfigured;

    @Schema(description = "本次是否成功解析（网络失败/未配置/解析不到均为 false）")
    private boolean success;

    private String provinceName;
    private String cityName;
    private String districtName;

    /**
     * 腾讯 geocoder 返回的格式化详细地址（result.address），用于地点名错位修复的存量回填。
     * 解析不到时为 null。
     */
    private String address;

    private String provinceCode;
    private String cityCode;
    private String districtCode;

    public static GeoResultVO notConfigured() {
        GeoResultVO vo = new GeoResultVO();
        vo.setGeoConfigured(false);
        vo.setSuccess(false);
        return vo;
    }

    public static GeoResultVO failed() {
        GeoResultVO vo = new GeoResultVO();
        vo.setGeoConfigured(true);
        vo.setSuccess(false);
        return vo;
    }
}
