package com.straycat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.CatProfile;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.AdoptionApplicationMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.service.AdoptionApplicationService;
import com.straycat.service.NotifyService;
import com.straycat.util.CatLifeStatus;
import com.straycat.util.NotifyTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AdoptionApplicationServiceImpl extends ServiceImpl<AdoptionApplicationMapper, AdoptionApplication> implements AdoptionApplicationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_REVIEWING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    private static final int MAX_WECHAT_LENGTH = 30;

    @Resource
    private CatProfileMapper catProfileMapper;

    @Resource
    private NotifyService notifyService;

    @Override
    public void apply(AdoptionApplication application) {
        CatProfile cat = catProfileMapper.selectById(application.getCatId());
        if (cat == null) {
            throw new BusinessException(ResultCode.CAT_NOT_FOUND);
        }
        if (cat.getLifeStatus() != null
                && (cat.getLifeStatus() == CatLifeStatus.ADOPTED || cat.getLifeStatus() == CatLifeStatus.FOLLOWING_UP)) {
            throw new BusinessException("这只猫咪已被领养");
        }
        application.setContactPhone(requirePhone(application.getContactPhone()));
        application.setContactWechat(cleanWechat(application.getContactWechat()));
        application.setStatus(STATUS_PENDING);
        application.setCreatedAt(LocalDateTime.now());
        save(application);
        moveCat(cat, CatLifeStatus.WAITING_ADOPTION);

        notifyService.sendToAdmins(MessageType.ADOPTION_NEW, application.getId(),
                notifyService.displayName(application.getApplicantId()) + " 申请领养「" + cat.getName() + "」，等待审核",
                NotifyTemplates.adoption(cat.getName(),
                        notifyService.displayName(application.getApplicantId()), "待审核"));
    }

    /**
     * 联系方式是审核时联系申请人用的，格式在服务端卡死，前端绕过也进不了库
     */
    private String requirePhone(String phone) {
        String value = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!value.matches(PHONE_PATTERN)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写正确的 11 位手机号");
        }
        return value;
    }

    private String cleanWechat(String wechat) {
        if (wechat == null) {
            return null;
        }
        String value = wechat.trim();
        if (value.length() > MAX_WECHAT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "微信号填写过长");
        }
        return value.isEmpty() ? null : value;
    }

    @Override
    public AdoptionApplication getApplication(Long id) {
        return getById(id);
    }

    @Override
    public void review(Long applicationId, Long reviewerId, Integer status, String comment) {
        if (applicationId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少领养申请 id");
        }
        if (status == null || (status != STATUS_APPROVED && status != STATUS_REJECTED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "审核结果只能是已通过或已拒绝");
        }
        AdoptionApplication application = getById(applicationId);
        if (application == null) {
            throw new BusinessException("领养申请不存在");
        }
        if (reviewerId.equals(application.getApplicantId())) {
            throw new BusinessException("不能审核自己提交的申请");
        }
        if (application.getStatus() != STATUS_PENDING && application.getStatus() != STATUS_REVIEWING) {
            throw new BusinessException("该申请已审核过");
        }

        application.setStatus(status);
        application.setReviewerId(reviewerId);
        application.setReviewComment(comment);
        application.setReviewedAt(LocalDateTime.now());
        updateById(application);

        CatProfile cat = catProfileMapper.selectById(application.getCatId());
        if (cat != null) {
            if (status == STATUS_APPROVED) {
                cat.setAdopterId(application.getApplicantId());
                // 申请人自己也有房有粮了，从此刻起进入已领养，回访期到了再置回访中
                moveCat(cat, CatLifeStatus.ADOPTED);
            } else {
                // 能被申请说明此前已有人在照料，拒绝就退回投喂中
                moveCat(cat, CatLifeStatus.FEEDING);
            }
        }

        boolean approved = status == STATUS_APPROVED;
        String catName = cat == null || cat.getName() == null ? "猫咪" : cat.getName();
        String reason = comment == null || comment.isBlank()
                ? (approved ? "请尽快与救助点联系接猫" : "可以继续在群里关注它")
                : comment;
        notifyService.send(approved ? MessageType.ADOPTION_APPROVED : MessageType.ADOPTION_REJECTED,
                application.getApplicantId(), application.getId(),
                "「" + catName + "」的领养申请" + (approved ? "已通过：" : "未通过：") + reason,
                NotifyTemplates.adoption(catName,
                        notifyService.displayName(application.getApplicantId()), reason));
    }

    private void moveCat(CatProfile cat, int target) {
        if (!CatLifeStatus.canMove(cat.getLifeStatus(), target)) {
            log.warn("猫咪 {} 生活状态 {} → {} 不在允许的流转内，跳过", cat.getId(), cat.getLifeStatus(), target);
            return;
        }
        cat.setLifeStatus(target);
        cat.setUpdatedAt(LocalDateTime.now());
        catProfileMapper.updateById(cat);
    }
}
