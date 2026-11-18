package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatTransferApply;
import com.straycat.entity.CatTransferPost;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CatTransferApplyMapper;
import com.straycat.mapper.CatTransferPostMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CatTransferService;
import com.straycat.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatTransferServiceImpl extends ServiceImpl<CatTransferPostMapper, CatTransferPost>
        implements CatTransferService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ONLINE = 1;
    private static final int STATUS_TRANSFERRED = 2;
    private static final int STATUS_OFFLINE = 3;
    private static final int STATUS_REJECTED = 4;
    private static final int MAX_ONLINE_POSTS = 3;
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    private final CatTransferApplyMapper applyMapper;
    private final NotifyService notifyService;
    private final SysUserMapper sysUserMapper;

    @Override
    public Page<CatTransferPost> publicPosts(Integer pageNum, Integer pageSize, String provinceCode,
                                              String cityCode, String districtCode, String breed, String keyword) {
        Page<CatTransferPost> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<CatTransferPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CatTransferPost::getStatus, STATUS_ONLINE);
        if (notBlank(provinceCode)) {
            wrapper.eq(CatTransferPost::getProvinceCode, provinceCode);
        }
        if (notBlank(cityCode)) {
            wrapper.eq(CatTransferPost::getCityCode, cityCode);
        }
        if (notBlank(districtCode)) {
            wrapper.eq(CatTransferPost::getDistrictCode, districtCode);
        }
        if (notBlank(breed)) {
            wrapper.like(CatTransferPost::getBreed, breed);
        }
        if (notBlank(keyword)) {
            wrapper.and(w -> w.like(CatTransferPost::getCatName, keyword)
                    .or().like(CatTransferPost::getBreed, keyword)
                    .or().like(CatTransferPost::getPersonality, keyword));
        }
        wrapper.orderByDesc(CatTransferPost::getCreatedAt);
        Page<CatTransferPost> result = baseMapper.selectPage(page, wrapper);
        result.getRecords().replaceAll(this::toPublic);
        return result;
    }

    @Override
    public CatTransferPost publicDetail(Long id, Long userId) {
        CatTransferPost post = baseMapper.selectById(id);
        if (post == null) {
            return null;
        }
        boolean owner = userId != null && userId.equals(post.getUserId());
        boolean admin = isAdmin(userId);
        // 待审/已转让/下架/驳回的信息，陌生人不能通过改 id 偷看
        if (!admin && !owner && (post.getStatus() == null || post.getStatus() != STATUS_ONLINE)) {
            return null;
        }
        try {
            baseMapper.incrementView(id);
        } catch (Exception e) {
            log.warn("转让详情浏览量自增失败: id={}, err={}", id, e.getMessage());
        }
        return toPublic(post);
    }

    @Override
    public CatTransferPost toPublic(CatTransferPost post) {
        if (post == null) {
            return null;
        }
        CatTransferPost copy = new CatTransferPost();
        BeanUtils.copyProperties(post, copy);
        // 隐私字段：任何公开接口都不下发
        copy.setContactPhone(null);
        copy.setContactName(null);
        return copy;
    }

    @Override
    public Long publish(CatTransferPost post, Long userId) {
        validatePost(post);
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<CatTransferPost>()
                .eq(CatTransferPost::getUserId, userId)
                .in(CatTransferPost::getStatus, STATUS_PENDING, STATUS_ONLINE));
        if (count != null && count >= MAX_ONLINE_POSTS) {
            throw new BusinessException("最多同时发布 3 条");
        }
        post.setUserId(userId);
        post.setStatus(STATUS_PENDING);
        post.setViewCount(0);
        post.setApplyCount(0);
        LocalDateTime now = LocalDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        save(post);
        return post.getId();
    }

    @Override
    public List<CatTransferPost> mine(Long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<CatTransferPost>()
                .eq(CatTransferPost::getUserId, userId)
                .orderByDesc(CatTransferPost::getCreatedAt));
    }

    @Override
    public void offline(Long id, Long userId) {
        CatTransferPost post = requireOwner(id, userId);
        post.setStatus(STATUS_OFFLINE);
        post.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(CatTransferApply apply, Long userId) {
        if (apply == null || apply.getPostId() == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少转让信息 id");
        }
        CatTransferPost post = baseMapper.selectById(apply.getPostId());
        if (post == null || post.getStatus() == null || post.getStatus() != STATUS_ONLINE) {
            throw new BusinessException("该转让信息当前不可申请");
        }
        if (userId.equals(post.getUserId())) {
            throw new BusinessException("不能申请自己发布的猫");
        }
        Long exists = applyMapper.selectCount(new LambdaQueryWrapper<CatTransferApply>()
                .eq(CatTransferApply::getPostId, apply.getPostId())
                .eq(CatTransferApply::getApplicantId, userId));
        if (exists != null && exists > 0) {
            throw new BusinessException("你已申请过这只猫");
        }
        String phone = requirePhone(apply.getContactPhone());
        apply.setContactPhone(phone);
        apply.setApplicantId(userId);
        apply.setStatus(0);
        apply.setCreatedAt(LocalDateTime.now());
        applyMapper.insert(apply);

        try {
            baseMapper.incrementApply(apply.getPostId());
        } catch (Exception e) {
            log.warn("转让申请数自增失败: postId={}, err={}", apply.getPostId(), e.getMessage());
        }
        try {
            String content = "【" + notifyService.displayName(userId) + "】申请接养你发布的「"
                    + post.getCatName() + "」。说明：" + (apply.getApplyNote() == null ? "" : apply.getApplyNote())
                    + "。联系电话：" + phone;
            notifyService.send(MessageType.CAT_TRANSFER_APPLY, post.getUserId(), post.getId(), content, Map.of());
        } catch (Exception e) {
            log.warn("转让申请通知失败: postId={}, err={}", post.getId(), e.getMessage());
        }
        return apply.getId();
    }

    @Override
    public List<CatTransferApply> myApplies(Long userId) {
        List<CatTransferApply> applies = applyMapper.selectList(new LambdaQueryWrapper<CatTransferApply>()
                .eq(CatTransferApply::getApplicantId, userId)
                .orderByDesc(CatTransferApply::getCreatedAt));
        if (applies.isEmpty()) {
            return applies;
        }
        Set<Long> postIds = applies.stream().map(CatTransferApply::getPostId).collect(Collectors.toSet());
        Map<Long, CatTransferPost> posts = baseMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(CatTransferPost::getId, Function.identity(), (a, b) -> a));
        for (CatTransferApply apply : applies) {
            CatTransferPost post = posts.get(apply.getPostId());
            String catName = post == null || post.getCatName() == null ? "猫咪" : post.getCatName();
            apply.setPostCatName(catName);
            apply.setPostTitle("「" + catName + "」找新家");
        }
        return applies;
    }

    @Override
    public List<CatTransferApply> appliesOfPost(Long postId, Long userId) {
        requireOwner(postId, userId);
        return applyMapper.selectList(new LambdaQueryWrapper<CatTransferApply>()
                .eq(CatTransferApply::getPostId, postId)
                .orderByDesc(CatTransferApply::getCreatedAt));
    }

    @Override
    public void updateApplyStatus(Long applyId, Integer status, Long userId) {
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "申请状态只能是 0待处理/1已联系/2不合适");
        }
        CatTransferApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        requireOwner(apply.getPostId(), userId);
        apply.setStatus(status);
        applyMapper.updateById(apply);
    }

    @Override
    public void complete(Long postId, Long userId) {
        CatTransferPost post = requireOwner(postId, userId);
        post.setStatus(STATUS_TRANSFERRED);
        post.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(post);

        List<CatTransferApply> applies = applyMapper.selectList(new LambdaQueryWrapper<CatTransferApply>()
                .eq(CatTransferApply::getPostId, postId));
        for (CatTransferApply apply : applies) {
            try {
                notifyService.send(MessageType.CAT_TRANSFER_CLOSED, apply.getApplicantId(), postId,
                        "你申请的「" + post.getCatName() + "」已找到新家，感谢你的关注。", Map.of());
            } catch (Exception e) {
                log.warn("转让完成通知失败: applyId={}, err={}", apply.getId(), e.getMessage());
            }
        }
    }

    @Override
    public Page<CatTransferPost> adminPosts(Integer status, Integer pageNum, Integer pageSize) {
        Page<CatTransferPost> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<CatTransferPost> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CatTransferPost::getStatus, status);
        }
        wrapper.orderByDesc(CatTransferPost::getCreatedAt);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public void audit(Long id, Integer status, String remark, Long reviewerId) {
        if (status == null || (status != STATUS_ONLINE && status != STATUS_REJECTED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "审核结果只能是 1通过 或 4驳回");
        }
        CatTransferPost post = baseMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("转让信息不存在");
        }
        post.setStatus(status);
        post.setReviewerId(reviewerId);
        post.setReviewRemark(remark);
        post.setReviewedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        baseMapper.updateById(post);
        if (status == STATUS_ONLINE) {
            try {
                notifyService.send(MessageType.ADOPTION_APPROVED, post.getUserId(), id,
                        "你发布的「" + post.getCatName() + "」已通过审核，现已在架展示。", Map.of());
            } catch (Exception e) {
                log.warn("转让审核通过通知失败: id={}, err={}", id, e.getMessage());
            }
        }
    }

    /** 详情里判断管理员：该接口匿名可读，不能依赖 RoleInterceptor 注入的 role 属性，按 userId 现查 */
    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && user.getRole() != null && user.getRole() >= 3;
    }

    /** 校验发布人身份，非本人一律 FORBIDDEN */
    private CatTransferPost requireOwner(Long id, Long userId) {
        CatTransferPost post = baseMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("转让信息不存在");
        }
        if (userId == null || !userId.equals(post.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有发布人可以操作");
        }
        return post;
    }

    private void validatePost(CatTransferPost post) {
        if (post == null || !notBlank(post.getCatName()) || !notBlank(post.getBreed())
                || !notBlank(post.getReason())) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请填写猫咪名字、品种和转让原因");
        }
        if (!notBlank(post.getPhotos())) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请至少上传 1 张照片");
        }
        post.setContactPhone(requirePhone(post.getContactPhone()));
    }

    private String requirePhone(String phone) {
        String value = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!value.matches(PHONE_PATTERN)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请填写正确的 11 位手机号");
        }
        return value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
