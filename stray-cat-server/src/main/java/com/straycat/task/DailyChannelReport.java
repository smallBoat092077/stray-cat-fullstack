package com.straycat.task;

import com.straycat.common.MessageType;
import com.straycat.service.NotifyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 渠道日报：每天凌晨把昨日的分享访问 / 新用户 / Top 活动 / Top 渠道汇总成一条站内消息
 * 发给全部管理员。只落站内消息（DAILY_REPORT 不配置订阅模板，天然不接微信推送）。
 * 幂等：当天已存在 DAILY_REPORT 消息则跳过，任务重复执行安全。
 */
@Slf4j
@Component
public class DailyChannelReport {

    @Value("${report.daily.enabled:true}")
    private boolean enabled;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private NotifyService notifyService;

    @Scheduled(cron = "${report.daily.cron:0 5 0 * * *}")
    public void report() {
        if (!enabled) {
            return;
        }
        try {
            if (alreadySentToday()) {
                return;
            }
            String content = buildContent();
            notifyService.sendToAdmins(MessageType.DAILY_REPORT, null, content, Map.of());
            log.info("渠道日报已发送");
        } catch (Exception e) {
            // 日报失败不能影响应用，明天自然会再跑
            log.warn("渠道日报生成失败: {}", e.getMessage());
        }
    }

    private boolean alreadySentToday() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_message WHERE type = ? AND created_at >= ?",
                Long.class, MessageType.DAILY_REPORT.name(), LocalDate.now().atStartOfDay());
        return count != null && count > 0;
    }

    private String buildContent() {
        LocalDateTime start = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay();
        String day = LocalDate.now().minusDays(1).toString();

        Map<String, Object> total = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS pv, COALESCE(SUM(v.is_new_user), 0) AS newUsers "
                        + "FROM share_visit v WHERE v.created_at >= ? AND v.created_at < ?", start, end);

        List<Map<String, Object>> campaigns = jdbcTemplate.queryForList(
                "SELECT IFNULL(l.campaign, '(未设置活动)') AS name, COUNT(*) AS pv, "
                        + "COALESCE(SUM(v.is_new_user), 0) AS newUsers "
                        + "FROM share_visit v LEFT JOIN share_link l ON l.sid = v.sid "
                        + "WHERE v.created_at >= ? AND v.created_at < ? "
                        + "GROUP BY IFNULL(l.campaign, '(未设置活动)') ORDER BY newUsers DESC, pv DESC LIMIT 3",
                start, end);

        List<Map<String, Object>> channels = jdbcTemplate.queryForList(
                "SELECT IFNULL(l.channel, '(未知渠道)') AS name, COUNT(*) AS pv, "
                        + "COALESCE(SUM(v.is_new_user), 0) AS newUsers "
                        + "FROM share_visit v LEFT JOIN share_link l ON l.sid = v.sid "
                        + "WHERE v.created_at >= ? AND v.created_at < ? "
                        + "GROUP BY IFNULL(l.channel, '(未知渠道)') ORDER BY pv DESC LIMIT 3",
                start, end);

        StringJoiner joiner = new StringJoiner("；");
        joiner.add("[" + day + " 渠道日报]");
        joiner.add("访问 " + total.get("pv") + " 次");
        joiner.add("带来新用户 " + total.get("newUsers") + " 位");
        if (!campaigns.isEmpty()) {
            joiner.add("热门活动 " + rankText(campaigns));
        }
        if (!channels.isEmpty()) {
            joiner.add("热门渠道 " + rankText(channels));
        }
        joiner.add("详见后台数据看板");
        return joiner.toString();
    }

    private String rankText(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(row.get("name")).append("(").append(row.get("pv")).append("次/")
                    .append(row.get("newUsers")).append("新)");
        }
        return sb.toString();
    }
}
