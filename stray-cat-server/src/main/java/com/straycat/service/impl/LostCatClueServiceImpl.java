package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.entity.LostCatClue;
import com.straycat.entity.LostCatPost;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.LostCatClueMapper;
import com.straycat.mapper.LostCatPostMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.request.LostCatClueRequest;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.LostCatClueService;
import com.straycat.service.NotifyService;
import com.straycat.vo.LostCatClueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LostCatClueServiceImpl implements LostCatClueService {

    private static final int AUDIT_PENDING = 0;
    private static final int AUDIT_PASSED = 1;
    private static final int AUDIT_REJECTED = 2;
    private static final int ROLE_ADMIN = 3;
    private static final int MAX_IMAGES = 6;

    private final LostCatClueMapper lostCatClueMapper;
    private final LostCatPostMapper lostCatPostMapper;
    private final SysUserMapper sysUserMapper;
    private final ContentSecurityService contentSecurityService;
    private final NotifyService notifyService;

    @Override
    public Long create(Long userId, LostCatClueRequest request) {
        if (request == null || request.getPostId() == null) {
            throw new BusinessException("关联启事不能为空");
        }
        if (request.getFoundAt() == null) {
            throw new BusinessException("发现时间不能为空");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessException("线索描述不能为空");
        }
        // 只给已审核通过的启事提线索
        LostCatPost post = lostCatPostMapper.selectById(request.getPostId());
        if (post == null || post.getAuditStatus() == null || post.getAuditStatus() != AUDIT_PASSED) {
            throw new BusinessException("启事不存在或未审核通过");
        }
        List<String> images = splitImages(request.getImages());
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException("照片最多 " + MAX_IMAGES + " 张");
        }

        // 自由文本（描述、地点文字）统一过一次内容安全检测（论坛/社交场景）
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL,
                request.getDescription(), request.getAddressDesc());

        LocalDateTime now = LocalDateTime.now();
        LostCatClue clue = new LostCatClue();
        clue.setPostId(request.getPostId());
        clue.setReporterId(userId);
        clue.setFoundAt(request.getFoundAt());
        clue.setLatitude(request.getLatitude());
        clue.setLongitude(request.getLongitude());
        clue.setAddressDesc(request.getAddressDesc());
        clue.setImages(images.isEmpty() ? null : String.join(",", images));
        clue.setDescription(request.getDescription());
        clue.setAuditStatus(AUDIT_PENDING);
        clue.setCreatedAt(now);
        clue.setUpdatedAt(now);
        lostCatClueMapper.insert(clue);
        return clue.getId();
    }

    @Override
    public LostCatClueVO detail(Long id, Long currentUserId) {
        LostCatClue clue = lostCatClueMapper.selectById(id);
        if (clue == null) {
            throw new BusinessException("线索不存在");
        }
        LostCatPost post = lostCatPostMapper.selectById(clue.getPostId());
        if (!canView(clue, post, currentUserId)) {
            // 不区分「不存在」与「无权」，避免借接口探测未审核线索是否存在
            throw new BusinessException("线索不存在或暂无权限查看");
        }
        return toVO(clue);
    }

    @Override
    public List<LostCatClueVO> mine(Long userId) {
        LambdaQueryWrapper<LostCatClue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LostCatClue::getReporterId, userId).orderByDesc(LostCatClue::getCreatedAt);
        return lostCatClueMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Page<LostCatClue> list(Integer auditStatus, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<LostCatClue> wrapper = new LambdaQueryWrapper<>();
        if (auditStatus != null) {
            wrapper.eq(LostCatClue::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(LostCatClue::getCreatedAt);
        return lostCatClueMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize), wrapper);
    }

    @Override
    public void audit(Long reviewerId, Long id, Integer auditStatus, String auditRemark) {
        if (auditStatus == null || (auditStatus != AUDIT_PASSED && auditStatus != AUDIT_REJECTED)) {
            throw new BusinessException("审核结果不合法（1 通过 / 2 驳回）");
        }
        if (auditStatus == AUDIT_REJECTED && (auditRemark == null || auditRemark.isBlank())) {
            throw new BusinessException("驳回需填写原因");
        }
        LostCatClue clue = lostCatClueMapper.selectById(id);
        if (clue == null) {
            throw new BusinessException("线索不存在");
        }

        LostCatClue update = new LostCatClue();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        update.setAuditRemark(truncate(auditRemark, 200));
        update.setReviewerId(reviewerId);
        update.setReviewedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        lostCatClueMapper.updateById(update);

        // 仅在审核通过时给【启事发布者】推站内消息；bizId 用线索 id，决定消息跳到线索详情页
        if (auditStatus == AUDIT_PASSED) {
            LostCatPost post = lostCatPostMapper.selectById(clue.getPostId());
            if (post != null && post.getUserId() != null) {
                String catName = post.getCatName();
                notifyService.send(MessageType.LOST_CAT_CLUE_NEW, post.getUserId(), clue.getId(),
                        "有人提交了关于「" + catName + "」的线索，审核已通过，点此查看");
            } else {
                log.warn("线索审核通过但关联启事缺失，跳过通知: clueId={}, postId={}", id, clue.getPostId());
            }
        }
        // 驳回只写 audit_remark，不推消息、不新增枚举
    }

    /**
     * 详情可见性（匿名放行的 GET，必须在这里自己卡住）：
     * 审核通过后对外可见（发布者从消息落地页点进来）；
     * 未审核 / 驳回线索仅「上报人本人 / 启事发布者 / 管理员」可见。
     */
    private boolean canView(LostCatClue clue, LostCatPost post, Long currentUserId) {
        if (clue.getAuditStatus() != null && clue.getAuditStatus() == AUDIT_PASSED) {
            return true;
        }
        if (currentUserId == null) {
            return false;
        }
        if (currentUserId.equals(clue.getReporterId())) {
            return true;
        }
        if (post != null && currentUserId.equals(post.getUserId())) {
            return true;
        }
        return isAdmin(currentUserId);
    }

    private boolean isAdmin(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && user.getRole() != null && user.getRole() == ROLE_ADMIN;
    }

    private LostCatClueVO toVO(LostCatClue c) {
        LostCatClueVO vo = new LostCatClueVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setFoundAt(c.getFoundAt());
        vo.setLatitude(c.getLatitude());
        vo.setLongitude(c.getLongitude());
        vo.setAddressDesc(c.getAddressDesc());
        vo.setImages(c.getImages());
        vo.setDescription(c.getDescription());
        vo.setAuditStatus(c.getAuditStatus());
        vo.setAuditRemark(c.getAuditRemark());
        vo.setCreatedAt(c.getCreatedAt());
        // 不下发 reporterId / reviewerId / 手机号 / openid 等任何联系方式
        return vo;
    }

    private List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
