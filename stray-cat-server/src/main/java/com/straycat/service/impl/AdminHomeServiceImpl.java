package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.HomeCache;
import com.straycat.entity.HomeContent;
import com.straycat.entity.LostCatPost;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.HomeContentMapper;
import com.straycat.mapper.LostCatPostMapper;
import com.straycat.request.HomeContentRequest;
import com.straycat.service.AdminHomeService;
import com.straycat.service.ContentSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminHomeServiceImpl implements AdminHomeService {

    /**
     * 站内页面路径正则（方案 C）：只允许 pages/ 开头、可选 query，拒绝一切外链。
     * 与 PosterController.LINK_PATTERN 略有差异，这里严格按已审核方案执行。
     */
    private static final Pattern LINK_PATTERN =
            Pattern.compile("^pages/[A-Za-z0-9_/-]+(\\?[A-Za-z0-9_=&-]+)?$");

    private final HomeContentMapper homeContentMapper;
    private final LostCatPostMapper lostCatPostMapper;
    private final HomeCache homeCache;
    private final ContentSecurityService contentSecurityService;

    @Override
    public Page<HomeContent> contentList(String type, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<HomeContent> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) {
            wrapper.eq(HomeContent::getType, type.trim().toUpperCase());
        }
        wrapper.orderByAsc(HomeContent::getSortOrder).orderByDesc(HomeContent::getCreatedAt);
        return homeContentMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize), wrapper);
    }

    @Override
    public HomeContent saveContent(Long adminId, HomeContentRequest request) {
        if (request == null) {
            throw new BusinessException("参数不能为空");
        }
        String type = request.getType() == null ? "" : request.getType().trim().toUpperCase();
        if (!"HOTSPOT".equals(type) && !"NOTICE".equals(type)) {
            throw new BusinessException("内容类型只能是 HOTSPOT 或 NOTICE");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException("标题不能为空");
        }

        String linkUrl = trimToNull(request.getLinkUrl());
        if (linkUrl != null && !LINK_PATTERN.matcher(linkUrl).matches()) {
            throw new BusinessException("链接必须是小程序内页面路径，如 pages/cat/detail?id=1");
        }

        // 后台表单文案同样过内容安全（方案 6.1）
        contentSecurityService.check(adminId, ContentSecurityService.SCENE_SOCIAL,
                request.getTitle(), request.getSummary(), request.getTag());

        LocalDateTime now = LocalDateTime.now();
        HomeContent entity;
        if (request.getId() == null) {
            entity = new HomeContent();
            entity.setPv(0);
            entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
            entity.setCreatedBy(adminId);
            entity.setCreatedAt(now);
        } else {
            entity = homeContentMapper.selectById(request.getId());
            if (entity == null) {
                throw new BusinessException("内容不存在");
            }
            if (request.getStatus() != null) {
                entity.setStatus(request.getStatus());
            }
        }

        entity.setType(type);
        entity.setTitle(truncate(request.getTitle(), 80));
        entity.setSummary(truncate(request.getSummary(), 200));
        entity.setCoverUrl(truncate(request.getCoverUrl(), 255));
        entity.setTag(truncate(request.getTag(), 20));
        entity.setLinkType(request.getLinkType() == null || request.getLinkType().isBlank()
                ? "none" : request.getLinkType().trim());
        entity.setLinkUrl(truncate(linkUrl, 255));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setPinned(request.getPinned() == null ? 0 : request.getPinned());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setUpdatedAt(now);

        if (request.getId() == null) {
            homeContentMapper.insert(entity);
        } else {
            homeContentMapper.updateById(entity);
        }
        evictBootstrap();
        return entity;
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态取值不合法");
        }
        HomeContent exist = homeContentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("内容不存在");
        }
        HomeContent update = new HomeContent();
        update.setId(id);
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        homeContentMapper.updateById(update);
        evictBootstrap();
    }

    @Override
    public void deleteContent(Long id) {
        HomeContent exist = homeContentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("内容不存在");
        }
        // 逻辑下线优先，保留内容以便恢复与审计，不做物理删除
        HomeContent update = new HomeContent();
        update.setId(id);
        update.setStatus(0);
        update.setUpdatedAt(LocalDateTime.now());
        homeContentMapper.updateById(update);
        evictBootstrap();
    }

    @Override
    public Page<LostCatPost> lostCatList(Integer auditStatus, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<LostCatPost> wrapper = new LambdaQueryWrapper<>();
        if (auditStatus != null) {
            wrapper.eq(LostCatPost::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(LostCatPost::getCreatedAt);
        return lostCatPostMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize), wrapper);
    }

    @Override
    public void auditLostCat(Long reviewerId, Long id, Integer auditStatus, String auditRemark) {
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            throw new BusinessException("审核结果不合法（1 通过 / 2 驳回）");
        }
        if (auditStatus == 2 && (auditRemark == null || auditRemark.isBlank())) {
            throw new BusinessException("驳回需填写原因");
        }
        LostCatPost exist = lostCatPostMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("启事不存在");
        }
        LostCatPost update = new LostCatPost();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        update.setAuditRemark(truncate(auditRemark, 200));
        update.setReviewerId(reviewerId);
        update.setReviewedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        lostCatPostMapper.updateById(update);
    }

    private void evictBootstrap() {
        homeCache.evictByPrefix(HomeCache.BOOTSTRAP_PREFIX);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
