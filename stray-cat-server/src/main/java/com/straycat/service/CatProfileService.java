package com.straycat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.CatProfile;

import java.util.List;

public interface CatProfileService extends IService<CatProfile> {

    /**
     * 查询附近猫咪
     */
    List<CatProfile> getNearbyCats(double latitude, double longitude, double radius);

    /**
     * 人气猫咪：点赞数优先、同分看浏览量，只取还能在地图上找到的猫（流浪中/投喂中/待领养）
     */
    List<CatProfile> getPopularCats(int limit);

    /**
     * 上报流浪猫
     */
    Long reportCat(CatProfile catProfile);

    /**
     * 更新猫咪状态
     */
    void updateLifeStatus(Long catId, Integer lifeStatus);

    /**
     * 详情：浏览量 +1，并回填当前用户点赞状态
     */
    CatProfile getDetail(Long catId, Long userId);

    /**
     * 点赞/取消点赞，返回操作后是否已点赞
     */
    boolean toggleLike(Long catId, Long userId);
}
