package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.MessageType;
import com.straycat.entity.Campaign;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CampaignMapper;
import com.straycat.service.CampaignService;
import com.straycat.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignServiceImpl extends ServiceImpl<CampaignMapper, Campaign> implements CampaignService {

    /** 活动代号与 share_link.campaign 对齐，限小写字母数字连字符，语义化好记好投 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,39}$");

    /** 单次提醒发送上限，防误操作把全量历史访客都打扰一遍 */
    private static final int REMIND_LIMIT = 500;

    private final CampaignMapper campaignMapper;
    private final JdbcTemplate jdbcTemplate;
    private final NotifyService notifyService;

    @Override
    public Page<Campaign> page(Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Campaign> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Campaign::getId);
        return campaignMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize), wrapper);
    }

    @Override
    public Campaign saveCampaign(Campaign form) {
        if (form == null || form.getCode() == null || form.getCode().isBlank()
                || form.getName() == null || form.getName().isBlank()) {
            throw new BusinessException("活动代号与名称不能为空");
        }
        String code = form.getCode().trim().toLowerCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException("活动代号限 2~40 位小写字母/数字/连字符");
        }
        form.setCode(code);
        form.setName(truncate(form.getName().trim(), 60));
        form.setDescription(truncate(form.getDescription(), 500));

        if (form.getId() == null) {
            LambdaQueryWrapper<Campaign> dup = new LambdaQueryWrapper<>();
            dup.eq(Campaign::getCode, code);
            if (campaignMapper.selectCount(dup) > 0) {
                throw new BusinessException("活动代号已存在");
            }
            form.setId(null);
            form.setStatus(form.getStatus() == null ? 1 : form.getStatus());
            form.setCreatedAt(LocalDateTime.now());
            form.setUpdatedAt(LocalDateTime.now());
            try {
                campaignMapper.insert(form);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("活动代号已存在");
            }
            return form;
        }

        Campaign exist = campaignMapper.selectById(form.getId());
        if (exist == null) {
            throw new BusinessException("活动不存在");
        }
        // code 已随物料投放，改了短码就对不上，编辑时禁改
        if (!exist.getCode().equals(code)) {
            throw new BusinessException("活动代号创建后不可修改");
        }
        exist.setName(form.getName());
        exist.setDescription(form.getDescription());
        exist.setCoverUrl(form.getCoverUrl());
        if (form.getStatus() != null) {
            exist.setStatus(form.getStatus());
        }
        exist.setStartTime(form.getStartTime());
        exist.setEndTime(form.getEndTime());
        exist.setUpdatedAt(LocalDateTime.now());
        campaignMapper.updateById(exist);
        return exist;
    }

    @Override
    public void toggleStatus(Long id, int status) {
        Campaign exist = campaignMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("活动不存在");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("状态取值不合法");
        }
        exist.setStatus(status);
        exist.setUpdatedAt(LocalDateTime.now());
        campaignMapper.updateById(exist);
    }

    @Override
    public Map<String, Object> detail(String code, Long id) {
        LambdaQueryWrapper<Campaign> wrapper = new LambdaQueryWrapper<>();
        if (code != null && !code.isBlank()) {
            wrapper.eq(Campaign::getCode, code.trim().toLowerCase());
        } else if (id != null) {
            wrapper.eq(Campaign::getId, id);
        } else {
            return null;
        }
        wrapper.last("LIMIT 1");
        Campaign campaign = campaignMapper.selectOne(wrapper);
        if (campaign == null) {
            return null;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", campaign.getId());
        data.put("code", campaign.getCode());
        data.put("name", campaign.getName());
        data.put("description", campaign.getDescription());
        data.put("coverUrl", campaign.getCoverUrl());
        data.put("status", campaign.getStatus());
        boolean ended = campaign.getStatus() == 0
                || (campaign.getEndTime() != null && campaign.getEndTime().isBefore(LocalDateTime.now()))
                || (campaign.getStartTime() != null && campaign.getStartTime().isAfter(LocalDateTime.now()));
        data.put("ended", ended);

        // 活动关联的猫咪：P0 里每挂一只猫就有一条 share_link（biz_type='cat'），去重取猫
        List<Long> catIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT l.biz_id FROM share_link l "
                        + "WHERE l.campaign = ? AND l.biz_type = 'cat' AND l.biz_id IS NOT NULL "
                        + "AND l.status = 1 ORDER BY l.biz_id",
                Long.class, campaign.getCode());
        List<Map<String, Object>> cats = List.of();
        if (!catIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(catIds.size(), "?"));
            cats = jdbcTemplate.queryForList(
                    "SELECT id, name, cover_image AS coverImage, life_status AS lifeStatus "
                            + "FROM cat_profile WHERE id IN (" + placeholders + ") ORDER BY id",
                    catIds.toArray());
        }
        data.put("cats", cats);

        // 热度：只下发两个数字，与隐私文案「活动短码仅统计、不关联身份」口径一致
        Map<String, Object> heat;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS pv, COALESCE(SUM(v.is_new_user), 0) AS newUsers "
                            + "FROM share_visit v JOIN share_link l ON l.sid = v.sid "
                            + "WHERE l.campaign = ?",
                    campaign.getCode());
            heat = rows.isEmpty() ? Map.of("pv", 0, "newUsers", 0) : rows.get(0);
        } catch (Exception e) {
            log.warn("活动热度查询失败: {}", e.getMessage());
            heat = Map.of("pv", 0, "newUsers", 0);
        }
        data.put("pv", heat.get("pv"));
        data.put("newUsers", heat.get("newUsers"));
        return data;
    }

    @Override
    public int sendReminder(Long campaignId, String content) {
        Campaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null || campaign.getStatus() == null || campaign.getStatus() != 1) {
            throw new BusinessException("活动不存在或已下线");
        }
        // 收件人 = 访问过本活动的用户（对活动有兴趣的人），不群发全站
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT v.visit_user_id FROM share_visit v "
                        + "JOIN share_link l ON l.sid = v.sid "
                        + "WHERE l.campaign = ? LIMIT " + REMIND_LIMIT,
                Long.class, campaign.getCode());
        if (userIds.isEmpty()) {
            return 0;
        }
        String text = content == null || content.isBlank()
                ? "「" + campaign.getName() + "」有新进展，来看看吧"
                : content.trim();
        // thing 类字段 20 字上限由 NotifyServiceImpl 统一截断；slot2 是给活动名以外的补充文案
        for (Long userId : userIds) {
            Map<String, String> templateData = Map.of(
                    "slot1", campaign.getName(),
                    "slot2", text,
                    "slot3", "点击进入活动页");
            notifyService.send(MessageType.CAMPAIGN_REMINDER, userId, campaign.getId(), text, templateData);
        }
        log.info("活动提醒已发送: campaign={}, code={}, 收件={}", campaign.getId(), campaign.getCode(), userIds.size());
        return userIds.size();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
