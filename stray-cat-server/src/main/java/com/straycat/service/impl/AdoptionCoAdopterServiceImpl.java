package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.AdoptionCoAdopter;
import com.straycat.entity.CatProfile;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.AdoptionApplicationMapper;
import com.straycat.mapper.AdoptionCoAdopterMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.AdoptionCoAdopterService;
import com.straycat.service.NotifyService;
import com.straycat.util.NotifyTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdoptionCoAdopterServiceImpl extends ServiceImpl<AdoptionCoAdopterMapper, AdoptionCoAdopter>
        implements AdoptionCoAdopterService {

    /**
     * 一只猫最多 4 个人共同照顾（主申请人 + 3 位共同领养人），再多没人分得清责任
     */
    private static final int MAX_CO_ADOPTERS = 3;

    private static final int APP_PENDING = 0;
    private static final int APP_REVIEWING = 1;
    private static final int APP_APPROVED = 2;
    private static final int APP_SIGNED = 4;

    @Resource
    private AdoptionApplicationMapper applicationMapper;

    @Resource
    private CatProfileMapper catProfileMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NotifyService notifyService;

    @Override
    public Long invite(Long applicationId, Long inviterId, String keyword) {
        if (applicationId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少领养申请 id");
        }
        String value = keyword == null ? "" : keyword.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请填写对方的账号名或昵称");
        }

        AdoptionApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("领养申请不存在");
        }
        if (!Objects.equals(application.getApplicantId(), inviterId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有申请人可以邀请共同领养人");
        }
        int appStatus = application.getStatus() == null ? APP_PENDING : application.getStatus();
        if (appStatus != APP_PENDING && appStatus != APP_REVIEWING
                && appStatus != APP_APPROVED && appStatus != APP_SIGNED) {
            throw new BusinessException("该申请当前状态不能邀请共同领养人");
        }

        SysUser target = resolveUser(value, inviterId);
        if (Objects.equals(target.getId(), inviterId)) {
            throw new BusinessException("不能邀请自己");
        }
        if (target.getStatus() != null && target.getStatus() == 0) {
            throw new BusinessException("该用户已被禁用");
        }

        AdoptionCoAdopter exists = getOne(new LambdaQueryWrapper<AdoptionCoAdopter>()
                .eq(AdoptionCoAdopter::getApplicationId, applicationId)
                .eq(AdoptionCoAdopter::getUserId, target.getId()));

        long activeCount = count(new LambdaQueryWrapper<AdoptionCoAdopter>()
                .eq(AdoptionCoAdopter::getApplicationId, applicationId)
                .ne(AdoptionCoAdopter::getStatus, AdoptionCoAdopter.STATUS_REJECTED));

        if (exists == null) {
            if (activeCount >= MAX_CO_ADOPTERS) {
                throw new BusinessException("共同领养人最多 " + MAX_CO_ADOPTERS + " 位");
            }
            AdoptionCoAdopter invite = new AdoptionCoAdopter();
            invite.setApplicationId(applicationId);
            invite.setCatId(application.getCatId());
            invite.setInviterId(inviterId);
            invite.setUserId(target.getId());
            invite.setStatus(AdoptionCoAdopter.STATUS_PENDING);
            invite.setCreatedAt(LocalDateTime.now());
            save(invite);
            notifyInvitee(invite, application, target);
            return invite.getId();
        }

        // 拒绝过的可以再邀请一次，别的状态说明邀请还挂着或已经同意了
        if (!Objects.equals(exists.getStatus(), AdoptionCoAdopter.STATUS_REJECTED)) {
            throw new BusinessException("已经邀请过该用户");
        }
        if (activeCount >= MAX_CO_ADOPTERS) {
            throw new BusinessException("共同领养人最多 " + MAX_CO_ADOPTERS + " 位");
        }
        exists.setStatus(AdoptionCoAdopter.STATUS_PENDING);
        exists.setRespondedAt(null);
        exists.setCreatedAt(LocalDateTime.now());
        updateById(exists);
        notifyInvitee(exists, application, target);
        return exists.getId();
    }

    /**
     * 先按账号名精确匹配，再按昵称精确匹配。
     * 昵称重名时宁可让人改用账号名，也不随便挑一个替用户做决定。
     */
    private SysUser resolveUser(String keyword, Long inviterId) {
        List<SysUser> byUsername = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, keyword)
                .last("LIMIT 2"));
        if (byUsername.size() == 1) {
            return byUsername.get(0);
        }
        List<SysUser> byNickname = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getNickname, keyword)
                .last("LIMIT 3"));
        if (byNickname.isEmpty()) {
            throw new BusinessException("没有找到该用户，请确认账号名或昵称");
        }
        if (byNickname.size() > 1) {
            throw new BusinessException("有多个同名用户，请让对方用账号名再邀请一次");
        }
        return byNickname.get(0);
    }

    private void notifyInvitee(AdoptionCoAdopter invite, AdoptionApplication application, SysUser target) {
        String catName = catNameOf(application.getCatId());
        String inviterName = notifyService.displayName(invite.getInviterId());
        // 记账邀请通知的字段是 邀请人/被邀请人/备注/时间，被邀请人填收信人自己的昵称
        String inviteeName = notifyService.displayName(target.getId());
        notifyService.send(MessageType.CO_ADOPTION_INVITE, target.getId(), invite.getId(),
                inviterName + " 邀请你共同领养「" + catName + "」，去领养页确认",
                NotifyTemplates.coAdoptInvite(inviterName, inviteeName,
                        "邀请你共同领养「" + catName + "」", invite.getCreatedAt()));
    }

    @Override
    public void respond(Long id, Long userId, boolean accept) {
        AdoptionCoAdopter invite = getById(id);
        if (invite == null) {
            throw new BusinessException("邀请不存在");
        }
        if (!Objects.equals(invite.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "这条邀请不是发给你的");
        }
        if (!Objects.equals(invite.getStatus(), AdoptionCoAdopter.STATUS_PENDING)) {
            throw new BusinessException("该邀请已处理过");
        }
        invite.setStatus(accept ? AdoptionCoAdopter.STATUS_ACCEPTED : AdoptionCoAdopter.STATUS_REJECTED);
        invite.setRespondedAt(LocalDateTime.now());
        updateById(invite);

        String catName = catNameOf(invite.getCatId());
        String responderName = notifyService.displayName(userId);
        notifyService.send(MessageType.CO_ADOPTION_RESULT, invite.getInviterId(), invite.getApplicationId(),
                responderName + (accept ? " 接受了" : " 拒绝了")
                        + "共同领养「" + catName + "」的邀请",
                // 审核结果通知的「任务名称」标签：值带上场景 —— 「狸花猫」共同领养
                NotifyTemplates.adoption("「" + catName + "」共同领养", responderName,
                        accept ? "已同意" : "已拒绝"));
        log.info("共同领养邀请 {} 由用户 {} {}", id, userId, accept ? "同意" : "拒绝");
    }

    @Override
    public void cancel(Long id, Long inviterId) {
        AdoptionCoAdopter invite = getById(id);
        if (invite == null) {
            throw new BusinessException("邀请不存在");
        }
        if (!Objects.equals(invite.getInviterId(), inviterId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有发起人可以撤回邀请");
        }
        if (!Objects.equals(invite.getStatus(), AdoptionCoAdopter.STATUS_PENDING)) {
            throw new BusinessException("对方已处理，无法撤回");
        }
        removeById(id);
    }

    @Override
    public List<AdoptionCoAdopter> listMine(Long userId) {
        List<AdoptionCoAdopter> list = list(new LambdaQueryWrapper<AdoptionCoAdopter>()
                .eq(AdoptionCoAdopter::getUserId, userId)
                .orderByDesc(AdoptionCoAdopter::getCreatedAt));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, String> catNames = loadCatNames(list.stream().map(AdoptionCoAdopter::getCatId).toList());
        Map<Long, String> names = loadNames(list.stream().map(AdoptionCoAdopter::getInviterId).toList());
        Map<Long, Integer> appStatuses = applicationMapper.selectBatchIds(
                        list.stream().map(AdoptionCoAdopter::getApplicationId).distinct().toList())
                .stream().collect(Collectors.toMap(AdoptionApplication::getId, AdoptionApplication::getStatus));
        for (AdoptionCoAdopter item : list) {
            item.setCatName(catNames.getOrDefault(item.getCatId(), "猫咪"));
            item.setInviterName(names.getOrDefault(item.getInviterId(), "好心人"));
            item.setApplicationStatus(appStatuses.get(item.getApplicationId()));
        }
        return list;
    }

    @Override
    public List<AdoptionCoAdopter> listByApplication(Long applicationId) {
        List<AdoptionCoAdopter> list = list(new LambdaQueryWrapper<AdoptionCoAdopter>()
                .eq(AdoptionCoAdopter::getApplicationId, applicationId)
                .orderByAsc(AdoptionCoAdopter::getCreatedAt));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, String> catNames = loadCatNames(list.stream().map(AdoptionCoAdopter::getCatId).toList());
        Map<Long, SysUser> users = new HashMap<>();
        sysUserMapper.selectBatchIds(list.stream().map(AdoptionCoAdopter::getUserId).distinct().toList())
                .forEach(user -> users.put(user.getId(), user));
        for (AdoptionCoAdopter item : list) {
            SysUser user = users.get(item.getUserId());
            item.setCatName(catNames.getOrDefault(item.getCatId(), "猫咪"));
            item.setUserName(user == null ? "好心人" : user.getNickname());
            item.setUserAvatar(user == null ? null : user.getAvatarUrl());
        }
        return list;
    }

    @Override
    public Map<Long, Integer> countAccepted(Collection<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Map.of();
        }
        List<AdoptionCoAdopter> rows = list(new LambdaQueryWrapper<AdoptionCoAdopter>()
                .in(AdoptionCoAdopter::getApplicationId, applicationIds)
                .eq(AdoptionCoAdopter::getStatus, AdoptionCoAdopter.STATUS_ACCEPTED));
        Map<Long, Integer> counts = new HashMap<>();
        for (AdoptionCoAdopter row : rows) {
            counts.merge(row.getApplicationId(), 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public void notifyCoAdopters(AdoptionApplication application, String catName, boolean approved, String reason) {
        List<AdoptionCoAdopter> accepted = list(new LambdaQueryWrapper<AdoptionCoAdopter>()
                .eq(AdoptionCoAdopter::getApplicationId, application.getId())
                .eq(AdoptionCoAdopter::getStatus, AdoptionCoAdopter.STATUS_ACCEPTED));
        String applicantName = notifyService.displayName(application.getApplicantId());
        // phrase 字段限 5 个汉字以内（审核结果通知模板），完整意见走站内消息
        Map<String, String> wxData = NotifyTemplates.adoption(catName, applicantName,
                approved ? "审核通过" : "审核未通过");
        for (AdoptionCoAdopter row : accepted) {
            notifyService.send(approved ? MessageType.ADOPTION_APPROVED : MessageType.ADOPTION_REJECTED,
                    row.getUserId(), application.getId(),
                    "你参与的共同领养「" + catName + "」" + (approved ? "已通过：" : "未通过：") + reason, wxData);
        }
    }

    private Map<Long, String> loadCatNames(List<Long> catIds) {
        List<Long> ids = catIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (CatProfile cat : catProfileMapper.selectBatchIds(ids)) {
            names.put(cat.getId(), cat.getName() == null ? "猫咪" : cat.getName());
        }
        return names;
    }

    private Map<Long, String> loadNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(ids)) {
            names.put(user.getId(), user.getNickname() == null ? "好心人" : user.getNickname());
        }
        return names;
    }

    private String catNameOf(Long catId) {
        if (catId == null) {
            return "猫咪";
        }
        CatProfile cat = catProfileMapper.selectById(catId);
        return cat == null || cat.getName() == null ? "猫咪" : cat.getName();
    }
}
