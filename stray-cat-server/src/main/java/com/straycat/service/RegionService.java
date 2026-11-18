package com.straycat.service;

import com.straycat.vo.GeoResultVO;

import java.util.List;

/**
 * 行政区域字典（区域划分需求 R1/R2）。region 表只读，由管理端 sync 写入。
 */
public interface RegionService {

    /**
     * 三级区域树（嵌套节点，内存缓存 10 分钟）：后台级联选择与名称映射共用。
     * 节点 {code, name, level, children}；直辖市省级直接挂区级。
     */
    List<java.util.Map<String, Object>> tree();

    /**
     * 某级行政区划的子列表：parentCode 为空返回省级列表；
     * 直辖市（省级下无市级子节点）自动下沉返回其区级列表。
     */
    List<com.straycat.entity.Region> childrenOf(String parentCode);

    /**
     * 按名称在字典内解析行政区码（逆地理编码的落点，避免猜测腾讯码格式）。
     * 返回 [provinceCode, cityCode, districtCode]，解析不到的位为 null；
     * 直辖市 cityName == provinceName 时 cityCode 取省级码。
     */
    String[] resolveCodes(String provinceName, String cityName, String districtName);

    /**
     * 管理端：从腾讯 district API 全量同步省市区到 region 表，幂等可重复执行。
     * 返回 {fetched, inserted}。
     */
    java.util.Map<String, Integer> syncFromTencent();

    /** 已同步条数 */
    long count();
}
