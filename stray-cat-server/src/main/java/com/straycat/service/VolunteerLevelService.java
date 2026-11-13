package com.straycat.service;

import com.straycat.vo.VolunteerRankVO;
import com.straycat.vo.VolunteerStatsVO;

import java.util.List;

public interface VolunteerLevelService {

    /**
     * 当前用户的等级面板：等级、服务时长、升级进度
     */
    VolunteerStatsVO stats(Long userId);

    /**
     * 按已完成打卡数重算等级，达到新等级时落库并发放一次性猫粮奖励。
     * 打卡审核通过后调用；查询等级面板时也会顺带刷新一次，兜住历史数据。
     */
    void refresh(Long userId);

    /**
     * 投喂排行榜：按审核通过的打卡次数排序，昵称脱敏
     */
    List<VolunteerRankVO> leaderboard(int limit);
}
