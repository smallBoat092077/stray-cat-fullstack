package com.straycat.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.straycat.common.MessageType;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.AdoptionFollowUp;
import com.straycat.entity.CatProfile;
import com.straycat.entity.UserMessage;
import com.straycat.mapper.AdoptionApplicationMapper;
import com.straycat.mapper.AdoptionFollowUpMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.UserMessageMapper;
import com.straycat.service.NotifyService;
import com.straycat.util.NotifyTemplates;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 领养回访到期提醒：审核通过后第 30/60/90 天各提醒一次领养人提交回访，同时给管理员下发待办。
 * 判重靠站内消息本身——同一申请 7 天内已有提醒记录就跳过，所以任务重复执行是安全的。
 */
@Slf4j
@Component
public class AdoptionFollowUpReminder {

    private static final int APPROVED = 2;
    private static final int[] STAGE_DAYS = {30, 60, 90};
    private static final int DEDUPE_WINDOW_DAYS = 7;

    @Value("${notify.follow-up-reminder.enabled:true}")
    private boolean enabled;

    @Resource
    private AdoptionApplicationMapper applicationMapper;

    @Resource
    private AdoptionFollowUpMapper followUpMapper;

    @Resource
    private CatProfileMapper catProfileMapper;

    @Resource
    private UserMessageMapper userMessageMapper;

    @Resource
    private NotifyService notifyService;

    @Scheduled(cron = "${notify.follow-up-reminder.cron:0 10 10 * * *}")
    public void remind() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (int stageDays : STAGE_DAYS) {
            LocalDateTime stageStart = today.minusDays(stageDays).atStartOfDay();
            LocalDateTime stageEnd = stageStart.plusDays(1);
            for (AdoptionApplication application : findApprovedBetween(stageStart, stageEnd)) {
                remindOne(application, stageDays, stageStart);
            }
        }
    }

    private List<AdoptionApplication> findApprovedBetween(LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<AdoptionApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdoptionApplication::getStatus, APPROVED)
                .ge(AdoptionApplication::getReviewedAt, from)
                .lt(AdoptionApplication::getReviewedAt, to);
        return applicationMapper.selectList(wrapper);
    }

    private void remindOne(AdoptionApplication application, int stageDays, LocalDateTime stageStart) {
        if (hasFollowUpSince(application.getId(), stageStart) || alreadyReminded(application.getId())) {
            return;
        }
        CatProfile cat = application.getCatId() == null ? null : catProfileMapper.selectById(application.getCatId());
        String catName = cat == null || cat.getName() == null ? "猫咪" : cat.getName();
        String stage = "领养满 " + stageDays + " 天";
        String content = "「" + catName + "」" + stage + "，请提交回访（猫咪近况、居住与环境、健康情况）";

        notifyService.send(MessageType.FOLLOW_UP_DUE, application.getApplicantId(), application.getId(),
                content, NotifyTemplates.followUp(catName, application.getReviewedAt(), stage));
        notifyService.sendToAdmins(MessageType.FOLLOW_UP_DUE, application.getId(),
                notifyService.displayName(application.getApplicantId()) + " 领养的「" + catName + "」" + stage + "，还没提交回访",
                NotifyTemplates.followUp(catName, application.getReviewedAt(), stage));
        log.info("回访提醒: application={}, stage={}天", application.getId(), stageDays);
    }

    private boolean hasFollowUpSince(Long applicationId, LocalDateTime stageStart) {
        LambdaQueryWrapper<AdoptionFollowUp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdoptionFollowUp::getApplicationId, applicationId)
                .ge(AdoptionFollowUp::getCreatedAt, stageStart);
        return followUpMapper.selectCount(wrapper) > 0;
    }

    private boolean alreadyReminded(Long applicationId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getType, MessageType.FOLLOW_UP_DUE.name())
                .eq(UserMessage::getBizId, applicationId)
                .ge(UserMessage::getCreatedAt, LocalDateTime.now().minusDays(DEDUPE_WINDOW_DAYS));
        return userMessageMapper.selectCount(wrapper) > 0;
    }
}
