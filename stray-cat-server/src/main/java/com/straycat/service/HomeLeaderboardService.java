package com.straycat.service;

import com.straycat.vo.MyRankVO;
import com.straycat.vo.RankItemVO;

import java.util.List;

/**
 * 首页志愿者三榜（方案 6.3）。结果与 type+period+limit 缓存 300s，mine 不缓存。
 */
public interface HomeLeaderboardService {

    /** type=feeding/food/active，period=week/month/all，limit≤50 */
    List<RankItemVO> leaderboard(String type, String period, Integer limit);

    /** 当前用户名次；未登录或数值为 0 返回 null */
    MyRankVO mine(String type, String period, Long userId);
}
