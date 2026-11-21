package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.interceptor.RoleInterceptor;
import com.straycat.entity.SysUser;
import com.straycat.entity.VolunteerApplication;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.SysUserMapper;
import com.straycat.mapper.VolunteerApplicationMapper;
import com.straycat.request.VolunteerApplyRequest;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.NotifyService;
import com.straycat.service.VolunteerApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolunteerApplicationServiceImpl implements VolunteerApplicationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int ROLE_VOLUNTEER = 1;

    private final VolunteerApplicationMapper applicationMapper;
    private final SysUserMapper sysUserMapper;
    private final NotifyService notifyService;
    private final ContentSecurityService contentSecurityService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long userId, VolunteerApplyRequest req) {
        if (req == null || isBlank(req.getRealName()) || isBlank(req.getContact()) || isBlank(req.getReason())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "姓名、联系方式和申请理由必填");
        }
        // 文本安全审核：姓名/联系方式/理由都过一遍
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL,
                req.getRealName(), req.getContact(), req.getReason());

        // 已有在途（待审核）申请则拒绝重复提交
        LambdaQueryWrapper<VolunteerApplication> dup = new LambdaQueryWrapper<>();
        dup.eq(VolunteerApplication::getApplicantId, userId)
                .eq(VolunteerApplication::getStatus, STATUS_PENDING);
        if (applicationMapper.selectCount(dup) > 0) {
            throw new BusinessException("你已有待审核的志愿者申请，请耐心等待结果");
        }
        // 已是志愿者无需再申请
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getRole() != null && user.getRole() >= ROLE_VOLUNTEER) {
            throw new BusinessException("你已是志愿者，无需重复申请");
        }

        VolunteerApplication app = new VolunteerApplication();
        app.setApplicantId(userId);
        app.setRealName(req.getRealName().trim());
        app.setContact(req.getContact().trim());
        app.setRegion(trimToNull(req.getRegion()));
        app.setServiceTime(trimToNull(req.getServiceTime()));
        app.setReason(req.getReason().trim());
        app.setStatus(STATUS_PENDING);
        app.setCreatedAt(LocalDateTime.now());
        applicationMapper.insert(app);

        String who = user == null ? "你" : (user.getNickname() == null ? "你" : user.getNickname());
        notifyService.send(MessageType.VOLUNTEER_APPLIED, userId, app.getId(),
                who + "，你的志愿者申请已提交，我们会尽快审核。");
        notifyService.sendToAdmins(MessageType.VOLUNTEER_NEW, app.getId(),
                "「" + app.getRealName() + "」提交了志愿者申请，请到后台审核。", Map.of());
        return app.getId();
    }

    @Override
    public VolunteerApplication myLatest(Long userId) {
        LambdaQueryWrapper<VolunteerApplication> w = new LambdaQueryWrapper<>();
        w.eq(VolunteerApplication::getApplicantId, userId)
                .orderByDesc(VolunteerApplication::getCreatedAt);
        List<VolunteerApplication> list = applicationMapper.selectList(w);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Page<VolunteerApplication> list(Integer pageNum, Integer pageSize, Integer status) {
        Page<VolunteerApplication> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<VolunteerApplication> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(VolunteerApplication::getStatus, status);
        }
        w.orderByDesc(VolunteerApplication::getCreatedAt);
        Page<VolunteerApplication> result = applicationMapper.selectPage(page, w);
        // 填申请人昵称
        for (VolunteerApplication app : result.getRecords()) {
            SysUser u = sysUserMapper.selectById(app.getApplicantId());
            app.setApplicantNickname(u == null ? null : u.getNickname());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long applicationId, Long reviewerId, Integer status, String comment) {
        if (applicationId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少申请 id");
        }
        if (status == null || (status != STATUS_APPROVED && status != STATUS_REJECTED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "审核结果只能是 1 通过或 2 拒绝");
        }
        VolunteerApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException("志愿者申请不存在");
        }
        if (app.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请已审核过");
        }

        app.setStatus(status);
        app.setReviewerId(reviewerId);
        app.setReviewComment(trimToNull(comment));
        app.setReviewedAt(LocalDateTime.now());
        applicationMapper.updateById(app);

        boolean approved = status == STATUS_APPROVED;
        if (approved) {
            // 幂等：仅把仍为普通用户的申请人升为志愿者
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, app.getApplicantId())
                    .eq(SysUser::getRole, 0)
                    .set(SysUser::getRole, ROLE_VOLUNTEER));
            // 同步清掉角色缓存，避免旧角色缓存 10 分钟内不生效
            try {
                redisTemplate.delete(RoleInterceptor.cacheKey(app.getApplicantId()));
            } catch (Exception e) {
                log.warn("清理角色缓存失败: {}", e.getMessage());
            }
        }

        String reason = trimToNull(comment);
        String tail = reason == null
                ? (approved ? "恭喜成为志愿者，更多专属任务已解锁。" : "可完善资料后再次申请。")
                : ("审核意见：" + reason);
        notifyService.send(approved ? MessageType.VOLUNTEER_APPROVED : MessageType.VOLUNTEER_REJECTED,
                app.getApplicantId(), app.getId(),
                "你的志愿者申请" + (approved ? "已通过。" : "未通过。") + tail);
    }

    @Override
    public Map<String, Long> counts() {
        Map<String, Long> m = new HashMap<>();
        m.put("pending", applicationMapper.selectCount(
                new LambdaQueryWrapper<VolunteerApplication>().eq(VolunteerApplication::getStatus, STATUS_PENDING)));
        m.put("approved", applicationMapper.selectCount(
                new LambdaQueryWrapper<VolunteerApplication>().eq(VolunteerApplication::getStatus, STATUS_APPROVED)));
        m.put("rejected", applicationMapper.selectCount(
                new LambdaQueryWrapper<VolunteerApplication>().eq(VolunteerApplication::getStatus, STATUS_REJECTED)));
        return m;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String trimToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }
}
