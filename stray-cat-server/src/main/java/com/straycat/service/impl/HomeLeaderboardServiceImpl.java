package com.straycat.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.straycat.common.HomeCache;
import com.straycat.entity.SysUser;
import com.straycat.mapper.HomeLeaderboardMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.HomeLeaderboardService;
import com.straycat.util.VolunteerLevel;
import com.straycat.vo.MyRankVO;
import com.straycat.vo.RankItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeLeaderboardServiceImpl implements HomeLeaderboardService {

    private static final String FEEDING = "feeding";
    private static final String FOOD = "food";
    private static final String ACTIVE = "active";

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 50;

    /** all 榜期用极大时间窗表示全量，SQL 仍统一走 [start,end) 参数 */
    private static final LocalDateTime ALL_START = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime ALL_END = LocalDateTime.of(2100, 1, 1, 0, 0);

    private static final TypeReference<List<RankItemVO>> BOARD_TYPE = new TypeReference<>() {
    };

    private final HomeLeaderboardMapper leaderboardMapper;
    private final SysUserMapper sysUserMapper;
    private final HomeCache homeCache;

    @Value("${home.leaderboard-cache-seconds:300}")
    private long boardCacheSeconds;

    @Override
    public List<RankItemVO> leaderboard(String type, String period, Integer limit) {
        String t = normalizeType(type);
        if (t == null) {
            return Collections.emptyList();
        }
        String p = normalizePeriod(period);
        int l = normalizeLimit(limit);
        String key = HomeCache.BOARD_PREFIX + t + ":" + p + ":" + l;

        List<RankItemVO> cached = homeCache.get(key, BOARD_TYPE);
        if (cached != null) {
            return cached;
        }

        Window window = window(p);
        List<Map<String, Object>> rows = switch (t) {
            case FEEDING -> leaderboardMapper.feedingBoard(window.start(), window.end(), l);
            case FOOD -> leaderboardMapper.foodBoard(window.start(), window.end(), l);
            default -> leaderboardMapper.activeBoard(window.start(), window.end(), l);
        };

        List<RankItemVO> items = decorate(rows, t);
        homeCache.set(key, items, boardCacheSeconds);
        return items;
    }

    @Override
    public MyRankVO mine(String type, String period, Long userId) {
        if (userId == null) {
            return null;
        }
        String t = normalizeType(type);
        if (t == null) {
            return null;
        }
        String p = normalizePeriod(period);
        Window window = window(p);

        long value = switch (t) {
            case FEEDING -> orZero(leaderboardMapper.feedingValue(userId, window.start(), window.end()));
            case FOOD -> orZero(leaderboardMapper.foodValue(userId, window.start(), window.end()));
            default -> orZero(leaderboardMapper.activeValue(userId, window.start(), window.end()));
        };
        // 方案 6.3：数值为 0 时不给名次，前端显示「登录后查看你的名次 ›」
        if (value <= 0) {
            return null;
        }

        long higher = switch (t) {
            case FEEDING -> orZero(leaderboardMapper.feedingHigherCount(value, userId, window.start(), window.end()));
            case FOOD -> orZero(leaderboardMapper.foodHigherCount(value, userId, window.start(), window.end()));
            default -> orZero(leaderboardMapper.activeHigherCount(value, userId, window.start(), window.end()));
        };

        MyRankVO vo = new MyRankVO();
        vo.setType(t);
        vo.setPeriod(p);
        vo.setRank((int) (higher + 1));
        vo.setValue(value);
        vo.setUnit(unit(t));
        vo.setUserId(userId);
        return vo;
    }

    /** 补昵称（脱敏）、等级名与名次，rank 按行号递增 */
    private List<RankItemVO> decorate(List<Map<String, Object>> rows, String type) {
        List<RankItemVO> items = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return items;
        }

        List<Long> userIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("userId");
            if (id != null) {
                userIds.add(Long.valueOf(id.toString()));
            }
        }
        Map<Long, SysUser> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Object idObj = row.get("userId");
            Object valObj = row.get("val");
            if (idObj == null) {
                continue;
            }
            long uid = Long.parseLong(idObj.toString());
            long value = valObj instanceof Number n ? n.longValue() : 0L;
            SysUser user = users.get(uid);

            RankItemVO vo = new RankItemVO();
            vo.setRank(i + 1);
            vo.setUserId(uid);
            vo.setNickname(mask(user == null ? null : user.getNickname()));
            vo.setLevelName(VolunteerLevel.byLevel(user == null ? null : user.getVolunteerLevel()).getTitle());
            vo.setValue(value);
            vo.setUnit(unit(type));
            items.add(vo);
        }
        return items;
    }

    /**
     * 时间窗统一 [start,end) 半开区间：week 近 7 天、month 自然月、all 全量。
     */
    private Window window(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case "week" -> new Window(now.minusDays(7), now);
            case "all" -> new Window(ALL_START, ALL_END);
            default -> {
                YearMonth ym = YearMonth.from(now);
                yield new Window(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay());
            }
        };
    }

    private String unit(String type) {
        return switch (type) {
            case FEEDING -> "次投喂";
            case FOOD -> "份猫粮";
            default -> "天";
        };
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return FEEDING;
        }
        String t = type.trim().toLowerCase();
        return switch (t) {
            case FEEDING, FOOD, ACTIVE -> t;
            default -> null;
        };
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "month";
        }
        String p = period.trim().toLowerCase();
        return switch (p) {
            case "week", "month", "all" -> p;
            default -> "month";
        };
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 昵称脱敏：与 VolunteerLevelServiceImpl.mask()（private）同逻辑——
     * 保留首字，其余用 * 代替，至少补一个 *。
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

    private record Window(LocalDateTime start, LocalDateTime end) {
    }
}
