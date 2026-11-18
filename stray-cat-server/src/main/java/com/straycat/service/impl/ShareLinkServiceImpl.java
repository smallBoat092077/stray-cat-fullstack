package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.straycat.dto.AttributionParam;
import com.straycat.entity.ShareLink;
import com.straycat.entity.ShareVisit;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.ShareLinkMapper;
import com.straycat.mapper.ShareVisitMapper;
import com.straycat.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkServiceImpl implements ShareLinkService {

    private static final Set<String> CHANNELS = Set.of(
            "mp_article", "mp_menu", "mp_card", "wxacode", "share_card", "offline");

    /** wxacode.getUnlimited 的 page：不带 / 、不带 query、必须是已发布页面 */
    private static final Pattern PAGE_PATTERN = Pattern.compile("^pages/[A-Za-z0-9_/-]+$");

    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SID_LENGTH = 10;
    private static final int QUOTA_PER_MINUTE = 30;

    private final ShareLinkMapper shareLinkMapper;
    private final ShareVisitMapper shareVisitMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public ShareLink createLink(String channel, String campaign, String bizType, Long bizId,
                                String landingPage, Long inviterId) {
        String safeChannel = (channel == null || channel.isBlank()) ? "share_card" : channel.trim();
        if (!CHANNELS.contains(safeChannel)) {
            throw new BusinessException("渠道取值不合法");
        }
        if (landingPage != null && !landingPage.isBlank()
                && !PAGE_PATTERN.matcher(landingPage.trim()).matches()) {
            throw new BusinessException("落地页必须是不带 / 且不带参数的已发布页面路径");
        }

        ShareLink link = new ShareLink();
        link.setChannel(safeChannel);
        link.setCampaign(truncate(campaign, 40));
        link.setBizType(truncate(bizType, 20));
        link.setBizId(bizId);
        link.setLandingPage(landingPage == null ? null : landingPage.trim());
        link.setInviterId(inviterId);
        link.setOwnerRole(inviterId == null ? 0 : 1);
        link.setStatus(1);
        link.setCreatedAt(LocalDateTime.now());

        for (int attempt = 0; attempt < 5; attempt++) {
            link.setId(null);
            link.setSid(randomSid());
            try {
                shareLinkMapper.insert(link);
                return link;
            } catch (DuplicateKeyException e) {
                log.debug("短码撞号，重试: attempt={}", attempt);
            }
        }
        throw new BusinessException("短码生成失败，请稍后重试");
    }

    @Override
    public ShareLink resolve(String sid) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        ShareLink link = shareLinkMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getSid, sid.trim())
                .eq(ShareLink::getStatus, 1)
                .last("LIMIT 1"));
        if (link == null) {
            return null;
        }
        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return link;
    }

    @Override
    public void applyAttribution(SysUser user, AttributionParam param) {
        if (user == null) {
            return;
        }
        ShareLink link = (param != null && param.hasSid()) ? resolve(param.getSid()) : null;
        if (link == null) {
            user.setUserSource("direct");
            if (param != null && param.hasSid()) {
                log.debug("短码无效，归因降级为 direct: sid={}", param.getSid());
            }
            return;
        }
        // 只认服务端表里的渠道/活动/邀请人，客户端传值不落库
        user.setUserSource(userSourceOf(link.getChannel()));
        user.setSourceSid(link.getSid());
        user.setInviterId(link.getInviterId());
    }

    @Override
    public void recordVisit(String sid, Long userId, boolean isNewUser, String entryType, String sceneValue) {
        if (sid == null || sid.isBlank() || userId == null) {
            return;
        }
        // 无效/过期/停用的 sid 不落访问明细，否则看板 LEFT JOIN 不到 share_link，
        // 会多出「(未设置活动)」的脏 PV，也可能被伪造短码刷量
        if (resolve(sid.trim()) == null) {
            log.debug("短码无效，跳过访问明细: sid={}", sid);
            return;
        }
        long exists = shareVisitMapper.selectCount(new LambdaQueryWrapper<ShareVisit>()
                .eq(ShareVisit::getSid, sid.trim())
                .eq(ShareVisit::getVisitUserId, userId));
        if (exists > 0) {
            return;
        }
        ShareVisit visit = new ShareVisit();
        visit.setSid(sid.trim());
        visit.setVisitUserId(userId);
        visit.setIsNewUser(isNewUser ? 1 : 0);
        visit.setEntryType(truncate(entryType, 16));
        visit.setSceneValue(truncate(sceneValue, 64));
        visit.setCreatedAt(LocalDateTime.now());
        try {
            shareVisitMapper.insert(visit);
        } catch (DuplicateKeyException e) {
            // 并发重复进入，唯一索引兜住即可
            log.debug("归因事件幂等命中: sid={}, userId={}", sid, userId);
        }
    }

    @Override
    public String userSourceOf(String channel) {
        if (channel == null) {
            return "direct";
        }
        return switch (channel) {
            case "mp_article", "mp_menu", "mp_card" -> "mp";
            case "share_card" -> "share";
            case "wxacode" -> "wxacode";
            case "offline" -> "offline";
            default -> "direct";
        };
    }

    @Override
    public Map<String, Object> channelStats(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", safeDays);

        data.put("sourceSummary", jdbcTemplate.queryForList(
                "SELECT IFNULL(user_source, 'direct') AS name, COUNT(*) AS users "
                        + "FROM sys_user WHERE created_at >= ? "
                        + "GROUP BY IFNULL(user_source, 'direct') ORDER BY users DESC", since));

        data.put("campaignRank", jdbcTemplate.queryForList(
                "SELECT IFNULL(l.campaign, '(未设置活动)') AS name, COUNT(*) AS pv, "
                        + "COALESCE(SUM(v.is_new_user), 0) AS newUsers, "
                        + "ROUND(COALESCE(SUM(v.is_new_user), 0) / COUNT(*), 4) AS activationRate "
                        + "FROM share_visit v LEFT JOIN share_link l ON l.sid = v.sid "
                        + "WHERE v.created_at >= ? "
                        + "GROUP BY IFNULL(l.campaign, '(未设置活动)') ORDER BY pv DESC", since));

        data.put("channelRank", jdbcTemplate.queryForList(
                "SELECT IFNULL(l.channel, '(未知渠道)') AS name, COUNT(*) AS pv, "
                        + "COALESCE(SUM(v.is_new_user), 0) AS newUsers, "
                        + "ROUND(COALESCE(SUM(v.is_new_user), 0) / COUNT(*), 4) AS activationRate "
                        + "FROM share_visit v LEFT JOIN share_link l ON l.sid = v.sid "
                        + "WHERE v.created_at >= ? "
                        + "GROUP BY IFNULL(l.channel, '(未知渠道)') ORDER BY pv DESC", since));

        data.put("dailyTrend", jdbcTemplate.queryForList(
                "SELECT DATE(v.created_at) AS day, COUNT(*) AS pv, "
                        + "COALESCE(SUM(v.is_new_user), 0) AS newUsers "
                        + "FROM share_visit v WHERE v.created_at >= ? "
                        + "GROUP BY DATE(v.created_at) ORDER BY day", since));

        // 无数据时的兜底，避免前端拿到空数组后表格全空却看不出是「还没有数据」
        data.put("hasData", !((List<?>) data.get("dailyTrend")).isEmpty());
        return data;
    }

    @Override
    public List<Map<String, Object>> linkList(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);
        try {
            return jdbcTemplate.queryForList(
                    "SELECT l.sid, l.channel, l.campaign, l.landing_page AS landingPage, "
                            + "l.inviter_id AS inviterId, l.created_at AS createdAt, "
                            + "COUNT(v.id) AS pv, COALESCE(SUM(v.is_new_user), 0) AS newUsers "
                            + "FROM share_link l LEFT JOIN share_visit v ON v.sid = l.sid "
                            + "WHERE l.created_at >= ? GROUP BY l.id ORDER BY l.id DESC LIMIT 100", since);
        } catch (Exception e) {
            log.warn("短码列表查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean tryAcquireQuota(String clientIp) {
        String key = "share:link:quota:" + (clientIp == null ? "unknown" : clientIp);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            return count != null && count <= QUOTA_PER_MINUTE;
        } catch (Exception e) {
            // Redis 抖动不该让分享功能整体不可用，放行但记日志
            log.warn("短码限流不可用，放行: {}", e.getMessage());
            return true;
        }
    }

    private static String randomSid() {
        StringBuilder sb = new StringBuilder(SID_LENGTH);
        for (int i = 0; i < SID_LENGTH; i++) {
            sb.append(BASE36[RANDOM.nextInt(BASE36.length)]);
        }
        return sb.toString();
    }

    private static String truncate(String raw, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return value.length() > max ? value.substring(0, max) : value;
    }
}
