package com.straycat.service;

import com.straycat.vo.WeatherVO;

/**
 * 首页天气代理（天气卡片功能设计 §3）。
 * key 只在后端使用；按 adcode 缓存；任何失败返回空 VO，不抛异常到前端。
 */
public interface WeatherService {

    /**
     * 按行政区码取归一化天气。
     *
     * @param adcode 国标 6 位行政区码，如 420111；为空/无效时用默认城市（武汉 420100）
     * @return 归一化天气；取不到数据时返回各字段为空的 VO（前端据此隐藏卡片）
     */
    WeatherVO getWeather(String adcode);
}
