package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatComment;
import com.straycat.entity.CatProfile;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CatCommentMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CatCommentService;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.NotifyService;
import com.straycat.util.PrivacyMask;
import com.straycat.vo.CatCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatCommentServiceImpl implements CatCommentService {

    private static final int CONTENT_MAX = 200;

    /** 一条评论默认带出的回复条数，其余走「展开更多回复」懒加载 */
    private static final int REPLY_PREVIEW = 3;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 单页上限，防止前端传个大 pageSize 把整只猫的评论一次拖走 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 站内通知里摘要的长度 */
    private static final int NOTIFY_SUMMARY = 30;

    private final CatCommentMapper catCommentMapper;

    private final CatProfileMapper catProfileMapper;

    private final SysUserMapper sysUserMapper;

    private final ContentSecurityService contentSecurityService;

    private final NotifyService notifyService;

    @Override
    public Page<CatCommentVO> listByCat(Long catId, Long viewerId, Integer pageNum, Integer pageSize) {
        Page<CatComment> tops = catCommentMapper.selectPage(new Page<>(pageNo(pageNum), size(pageSize)),
                new LambdaQueryWrapper<CatComment>()
                        .eq(CatComment::getCatId, catId)
                        .eq(CatComment::getParentId, CatComment.ROOT)
                        .eq(CatComment::getStatus, CatComment.STATUS_NORMAL)
                        .orderByDesc(CatComment::getCreatedAt)
                        .orderByDesc(CatComment::getId));

        Page<CatCommentVO> target = new Page<>(tops.getCurrent(), tops.getSize(), tops.getTotal());
        List<CatComment> records = tops.getRecords();
        if (records.isEmpty()) {
            target.setRecords(List.of());
            return target;
        }

        List<Long> topIds = records.stream().map(CatComment::getId).collect(Collectors.toList());
        // 一次把这页评论楼下的回复全捞出来在内存分组。本项目单页 20 楼，
        // 回复量级很小；真出现几百回复的帖子，再改成按楼分页查。
        List<CatComment> replies = catCommentMapper.selectList(new LambdaQueryWrapper<CatComment>()
                .in(CatComment::getParentId, topIds)
                .eq(CatComment::getStatus, CatComment.STATUS_NORMAL)
                .orderByAsc(CatComment::getCreatedAt)
                .orderByAsc(CatComment::getId));
        Map<Long, List<CatComment>> threads = replies.stream()
                .collect(Collectors.groupingBy(CatComment::getParentId));

        List<CatComment> all = new ArrayList<>(records);
        all.addAll(replies);
        Map<Long, CatCommentVO> voById = decorate(all, viewerId);

        target.setRecords(records.stream().map(comment -> {
            CatCommentVO vo = voById.get(comment.getId());
            List<CatComment> thread = threads.getOrDefault(comment.getId(), List.of());
            vo.setReplyCount(thread.size());
            vo.setReplies(thread.stream()
                    .limit(REPLY_PREVIEW)
                    .map(reply -> voById.get(reply.getId()))
                    .collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList()));
        return target;
    }

    @Override
    public Page<CatCommentVO> listReplies(Long parentId, Long viewerId, Integer pageNum, Integer pageSize) {
        CatComment parent = requireNormal(parentId);
        if (!isRoot(parent.getParentId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "只能展开顶级评论下的回复");
        }
        Page<CatComment> source = catCommentMapper.selectPage(new Page<>(pageNo(pageNum), size(pageSize)),
                new LambdaQueryWrapper<CatComment>()
                        .eq(CatComment::getParentId, parent.getId())
                        .eq(CatComment::getStatus, CatComment.STATUS_NORMAL)
                        .orderByAsc(CatComment::getCreatedAt)
                        .orderByAsc(CatComment::getId));

        Page<CatCommentVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        Map<Long, CatCommentVO> voById = decorate(source.getRecords(), viewerId);
        target.setRecords(source.getRecords().stream()
                .map(comment -> voById.get(comment.getId()))
                .collect(Collectors.toList()));
        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatCommentVO create(Long userId, Long catId, CatComment form) {
        CatProfile cat = catProfileMapper.selectById(catId);
        if (cat == null) {
            throw new BusinessException(ResultCode.CAT_NOT_FOUND);
        }
        String content = normalize(form == null ? null : form.getContent());

        Long parentId = CatComment.ROOT;
        Long replyToUserId = null;
        Long requestedParent = form == null ? null : form.getParentId();
        if (!isRoot(requestedParent)) {
            // parentId 是「我点的那条」，可以是楼主也可以是楼内任意一条回复
            CatComment target = requireNormal(requestedParent);
            if (!Objects.equals(target.getCatId(), catId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "回复的评论不属于这只猫咪");
            }
            // 回复的回复归到同一个楼下，层级不再加深；被 @ 的人就是我点的那条的作者
            parentId = isRoot(target.getParentId()) ? target.getId() : target.getParentId();
            replyToUserId = target.getUserId();
        }

        contentSecurityService.check(userId, ContentSecurityService.SCENE_COMMENT, content);

        CatComment comment = new CatComment();
        comment.setCatId(catId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setStatus(CatComment.STATUS_NORMAL);
        comment.setCreatedAt(LocalDateTime.now());
        catCommentMapper.insert(comment);
        catProfileMapper.addCommentCount(catId, 1);

        if (!isRoot(parentId)) {
            notifyReply(comment, cat, userId, content);
        }
        return decorate(List.of(comment), userId).get(comment.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Integer role, Long commentId) {
        CatComment comment = catCommentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getStatus(), CatComment.STATUS_NORMAL)) {
            return;
        }
        boolean admin = role != null && role >= 3;
        if (!admin && !comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        List<Long> ids = new ArrayList<>();
        ids.add(comment.getId());
        if (isRoot(comment.getParentId())) {
            // 楼主被删后整楼无处展示，回复一起下架，计数才不会虚高
            catCommentMapper.selectList(new LambdaQueryWrapper<CatComment>()
                            .select(CatComment::getId)
                            .eq(CatComment::getParentId, comment.getId())
                            .eq(CatComment::getStatus, CatComment.STATUS_NORMAL))
                    .forEach(reply -> ids.add(reply.getId()));
        }

        catCommentMapper.update(null, new LambdaUpdateWrapper<CatComment>()
                .set(CatComment::getStatus, CatComment.STATUS_DELETED)
                .in(CatComment::getId, ids));
        catProfileMapper.addCommentCount(comment.getCatId(), -ids.size());
    }

    private void notifyReply(CatComment comment, CatProfile cat, Long userId, String content) {
        Long target = comment.getReplyToUserId();
        if (target == null || target.equals(userId)) {
            return;
        }
        String summary = content.length() > NOTIFY_SUMMARY ? content.substring(0, NOTIFY_SUMMARY) + "…" : content;
        // 没有为评论配微信订阅模板，只落站内消息中心
        notifyService.send(MessageType.COMMENT_REPLY, target, cat.getId(),
                notifyService.displayName(userId) + " 回复了你关于「" + cat.getName() + "」的评论：" + summary);
    }

    /**
     * 批量补齐昵称头像：一次把评论作者和被回复人一起查出来，不做 N+1
     */
    private Map<Long, CatCommentVO> decorate(List<CatComment> comments, Long viewerId) {
        Map<Long, CatCommentVO> result = new LinkedHashMap<>();
        if (comments.isEmpty()) {
            return result;
        }
        Set<Long> userIds = new HashSet<>();
        for (CatComment comment : comments) {
            userIds.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) {
                userIds.add(comment.getReplyToUserId());
            }
        }
        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, user -> user));

        for (CatComment comment : comments) {
            CatCommentVO vo = new CatCommentVO();
            vo.setId(comment.getId());
            vo.setCatId(comment.getCatId());
            vo.setParentId(comment.getParentId());
            vo.setContent(comment.getContent());
            vo.setCreatedAt(comment.getCreatedAt());
            SysUser author = users.get(comment.getUserId());
            vo.setNickname(PrivacyMask.nickname(author == null ? null : author.getNickname()));
            vo.setAvatar(author == null ? null : author.getAvatarUrl());
            if (comment.getReplyToUserId() != null) {
                SysUser replied = users.get(comment.getReplyToUserId());
                vo.setReplyToNickname(PrivacyMask.nickname(replied == null ? null : replied.getNickname()));
            }
            vo.setMine(viewerId != null && viewerId.equals(comment.getUserId()));
            result.put(comment.getId(), vo);
        }
        return result;
    }

    private CatComment requireNormal(Long commentId) {
        CatComment comment = commentId == null ? null : catCommentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getStatus(), CatComment.STATUS_NORMAL)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "评论不存在或已被删除");
        }
        return comment;
    }

    private String normalize(String raw) {
        String content = raw == null ? "" : raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (content.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "评论内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "评论内容最多 " + CONTENT_MAX + " 字");
        }
        return content;
    }

    private boolean isRoot(Long parentId) {
        return parentId == null || parentId == CatComment.ROOT;
    }

    private long pageNo(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private long size(Integer pageSize) {
        int value = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        return Math.min(value, MAX_PAGE_SIZE);
    }
}
