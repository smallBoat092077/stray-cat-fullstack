package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.straycat.entity.FeedingRecord;
import com.straycat.entity.SysUser;
import com.straycat.mapper.FeedingRecordMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CoinService;
import com.straycat.service.VolunteerLevelService;
import com.straycat.util.VolunteerLevel;
import com.straycat.vo.VolunteerRankVO;
import com.straycat.vo.VolunteerStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolunteerLevelServiceImpl implements VolunteerLevelService {

    private static final int RECORD_APPROVED = 1;
    private static final int MAX_LEADERBOARD = 50;

    private final FeedingRecordMapper feedingRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final CoinService coinService;

    @Override
    public VolunteerStatsVO stats(Long userId) {
        // 进来先刷一次：历史账号没有等级列，老打卡也不会触发升级检查
        refresh(userId);

        SysUser user = sysUserMapper.selectById(userId);
        VolunteerLevel level = VolunteerLevel.byLevel(user == null ? null : user.getVolunteerLevel());
        int completed = countApproved(userId);
        VolunteerLevel next = level.next();

        VolunteerStatsVO vo = new VolunteerStatsVO();
        vo.setUserId(userId);
        vo.setNickname(user == null ? null : user.getNickname());
        vo.setLevel(level.getLevel());
        vo.setLevelName(level.getTitle());
        vo.setCompletedCount(completed);
        vo.setServiceHours(roundHours(completed));
        vo.setCreditScore(user == null || user.getCreditScore() == null ? 100 : user.getCreditScore());
        vo.setNextLevelName(next == null ? null : next.getTitle());
        vo.setNextLevelCompleted(next == null ? null : next.getMinCompleted());
        vo.setRemainToNext(next == null ? 0 : Math.max(0, next.getMinCompleted() - completed));
        vo.setProgressPercent(progress(level, next, completed));
        vo.setNextReward(next == null ? 0 : next.getCoinReward());
        return vo;
    }

    @Override
    public void refresh(Long userId) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int completed = countApproved(userId);
        VolunteerLevel target = VolunteerLevel.of(completed);
        int current = user.getVolunteerLevel() == null ? 1 : user.getVolunteerLevel();
        if (target.getLevel() <= current) {
            return;
        }

        // 一次跨多级时把各档奖励累加，bizId 用目标等级，重复刷新不会重复发
        int reward = 0;
        for (VolunteerLevel item : VolunteerLevel.values()) {
            if (item.getLevel() > current && item.getLevel() <= target.getLevel()) {
                reward += item.getCoinReward();
            }
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setVolunteerLevel(target.getLevel());
        sysUserMapper.updateById(update);
        if (reward > 0) {
            coinService.grant(userId, reward, CoinService.BIZ_VOLUNTEER_LEVEL,
                    (long) target.getLevel(), "志愿者升级为" + target.getTitle());
        }
        log.info("志愿者等级提升: user={}, {} → {} ({}), 奖励猫粮 {}", userId, current, target.getLevel(),
                target.getTitle(), reward);
    }

    @Override
    public List<VolunteerRankVO> leaderboard(int limit) {
        int size = limit <= 0 ? 10 : Math.min(limit, MAX_LEADERBOARD);
        QueryWrapper<FeedingRecord> wrapper = new QueryWrapper<>();
        wrapper.select("volunteer_id AS volunteerId", "COUNT(*) AS doneCount")
                .eq("status", RECORD_APPROVED)
                .isNotNull("volunteer_id")
                .groupBy("volunteer_id")
                .orderByDesc("doneCount")
                .last("LIMIT " + size);

        List<VolunteerRankVO> ranks = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Map<String, Object> row : feedingRecordMapper.selectMaps(wrapper)) {
            Object id = row.get("volunteerId");
            Object count = row.get("doneCount");
            if (id == null) {
                continue;
            }
            userIds.add(Long.valueOf(id.toString()));
            counts.add(count == null ? 0 : Integer.parseInt(count.toString()));
        }
        if (userIds.isEmpty()) {
            return ranks;
        }

        Map<Long, SysUser> users = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
            users.put(user.getId(), user);
        }
        for (int i = 0; i < userIds.size(); i++) {
            SysUser user = users.get(userIds.get(i));
            VolunteerLevel level = VolunteerLevel.byLevel(user == null ? null : user.getVolunteerLevel());
            VolunteerRankVO vo = new VolunteerRankVO();
            vo.setRank(i + 1);
            vo.setUserId(userIds.get(i));
            vo.setNickname(mask(user == null ? null : user.getNickname()));
            vo.setLevel(level.getLevel());
            vo.setLevelName(level.getTitle());
            vo.setCompletedCount(counts.get(i));
            vo.setServiceHours(roundHours(counts.get(i)));
            ranks.add(vo);
        }
        return ranks;
    }

    private int countApproved(Long userId) {
        Long count = feedingRecordMapper.selectCount(new QueryWrapper<FeedingRecord>()
                .eq("volunteer_id", userId)
                .eq("status", RECORD_APPROVED));
        return count == null ? 0 : count.intValue();
    }

    private double roundHours(int completed) {
        return Math.round(completed * VolunteerLevel.HOURS_PER_CHECKIN * 10) / 10.0;
    }

    private int progress(VolunteerLevel current, VolunteerLevel next, int completed) {
        if (next == null) {
            return 100;
        }
        int span = next.getMinCompleted() - current.getMinCompleted();
        if (span <= 0) {
            return 100;
        }
        int done = completed - current.getMinCompleted();
        return Math.max(0, Math.min(100, done * 100 / span));
    }

    /**
     * 昵称脱敏：保留首字，其余用 * 代替，至少补一个 *
     */
    private String mask(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "好心人";
        }
        String name = nickname.trim();
        if (name.length() == 1) {
            return name + "*";
        }
        return name.charAt(0) + "**";
    }
}
