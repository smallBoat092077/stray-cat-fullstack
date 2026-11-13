package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.CatProfile;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.AdoptionApplicationMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.service.AdoptionApplicationService;
import com.straycat.service.AdoptionCoAdopterService;
import com.straycat.service.NotifyService;
import com.straycat.util.CatLifeStatus;
import com.straycat.util.NotifyTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Resource
    private AdoptionCoAdopterService coAdopterService;

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
        // 同一个人对同一只猫只能有一张在途申请，否则审核台会出现重复单、管理员被重复通知
        Long pendingCount = baseMapper.selectCount(new LambdaQueryWrapper<AdoptionApplication>()
                .eq(AdoptionApplication::getApplicantId, application.getApplicantId())
                .eq(AdoptionApplication::getCatId, application.getCatId())
                .in(AdoptionApplication::getStatus, STATUS_PENDING, STATUS_REVIEWING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(ResultCode.ADOPTION_ALREADY_APPLIED);
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
        AdoptionApplication application = getById(id);
        if (application != null) {
            fillDisplayInfo(List.of(application));
        }
        return application;
    }

    /**
     * 审核接口。通过必须排他：一只猫只能归属一个领养人，先通过者得，
     * 其余在途申请同事务内自动驳回并通知（见 claimCatExclusively / autoRejectOthers）。
     * 通知靠 NotifyServiceImpl 的 afterCommit 挂钩，事务回滚时不会发出半截消息。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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
        boolean approved = status == STATUS_APPROVED;
        if (cat != null) {
            if (approved) {
                // 排他归属：条件更新抢占，抢不到说明别的申请已经先通过了
                claimCatExclusively(application);
            } else {
                // 能被申请说明此前已有人在照料，拒绝就退回投喂中
                moveCat(cat, CatLifeStatus.FEEDING);
            }
        }

        String catName = cat == null || cat.getName() == null ? "猫咪" : cat.getName();
        String reason = comment == null || comment.isBlank()
                ? (approved ? "请尽快与救助点联系接猫" : "可以继续在群里关注它")
                : comment;
        // phrase 字段限 5 个汉字以内的固定短语，自由评语放不进去（详见 NotifyTemplates），
        // 所以微信通知里「审核结果」传固定短语，完整意见仍走站内消息
        String phrase = approved ? "审核通过" : "审核未通过";
        notifyService.send(approved ? MessageType.ADOPTION_APPROVED : MessageType.ADOPTION_REJECTED,
                application.getApplicantId(), application.getId(),
                "「" + catName + "」的领养申请" + (approved ? "已通过：" : "未通过：") + reason,
                NotifyTemplates.adoption(catName,
                        notifyService.displayName(application.getApplicantId()), phrase));

        // 共同领养人也该知道结果，不然只能等主申请人转告
        coAdopterService.notifyCoAdopters(application, catName, approved, reason);

        if (approved) {
            // 猫已有归属，同猫其余在途申请自动驳回，避免管理员再点通过把人搞混
            autoRejectOthers(application, reviewerId, catName);
        }
    }

    /**
     * 通过审核时的猫咪归属抢占。用数据库条件更新（adopter_id 为空且未进入已领养/回访中）
     * 做原子判断：并发下两个管理员同时点通过，只有一个 UPDATE 能命中。
     * 影响行数为 0 就抛业务异常，整个事务回滚，本申请保持待审核，管理员收到明确提示。
     */
    private void claimCatExclusively(AdoptionApplication application) {
        LambdaUpdateWrapper<CatProfile> claim = new LambdaUpdateWrapper<CatProfile>()
                .eq(CatProfile::getId, application.getCatId())
                .isNull(CatProfile::getAdopterId)
                // life_status 允许为 NULL（canMove 把 NULL 当流浪中），所以 NULL 也要放行
                .and(w -> w.isNull(CatProfile::getLifeStatus)
                        .or().notIn(CatProfile::getLifeStatus,
                                CatLifeStatus.ADOPTED, CatLifeStatus.FOLLOWING_UP))
                .set(CatProfile::getAdopterId, application.getApplicantId())
                .set(CatProfile::getLifeStatus, CatLifeStatus.ADOPTED)
                .set(CatProfile::getUpdatedAt, LocalDateTime.now());
        int claimed = catProfileMapper.update(null, claim);
        if (claimed == 0) {
            throw new BusinessException("这只猫已被领养给其他申请人，请驳回本申请");
        }
    }

    /**
     * 通过某张申请后，把同猫其余待审核/审核中的申请批量驳回（系统自动，非人为否决）。
     * 申请人收到微信推送「审核未通过」+ 站内信说明真实原因，共同领养人同步通知。
     */
    private void autoRejectOthers(AdoptionApplication approved, Long reviewerId, String catName) {
        List<AdoptionApplication> others = list(new LambdaQueryWrapper<AdoptionApplication>()
                .eq(AdoptionApplication::getCatId, approved.getCatId())
                .ne(AdoptionApplication::getId, approved.getId())
                .in(AdoptionApplication::getStatus, STATUS_PENDING, STATUS_REVIEWING));
        for (AdoptionApplication other : others) {
            other.setStatus(STATUS_REJECTED);
            other.setReviewerId(reviewerId);
            other.setReviewComment("该猫咪已领养给其他申请人");
            other.setReviewedAt(LocalDateTime.now());
            updateById(other);
            notifyService.send(MessageType.ADOPTION_REJECTED, other.getApplicantId(), other.getId(),
                    "「" + catName + "」的领养申请未通过：该猫咪已被其他申请人领养",
                    NotifyTemplates.adoption(catName,
                            notifyService.displayName(other.getApplicantId()), "审核未通过"));
            coAdopterService.notifyCoAdopters(other, catName, false, "该猫咪已被其他申请人领养");
        }
    }

    @Override
    public void fillDisplayInfo(List<AdoptionApplication> applications) {
        if (applications == null || applications.isEmpty()) {
            return;
        }
        List<Long> catIds = applications.stream()
                .map(AdoptionApplication::getCatId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> catNames = new HashMap<>();
        if (!catIds.isEmpty()) {
            for (CatProfile cat : catProfileMapper.selectBatchIds(catIds)) {
                catNames.put(cat.getId(), cat.getName());
            }
        }
        Map<Long, Integer> counts = coAdopterService.countAccepted(
                applications.stream().map(AdoptionApplication::getId).collect(Collectors.toList()));
        for (AdoptionApplication application : applications) {
            String name = catNames.get(application.getCatId());
            application.setCatName(name == null || name.isBlank() ? "猫咪" : name);
            application.setCoAdopterCount(counts.getOrDefault(application.getId(), 0));
        }
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
