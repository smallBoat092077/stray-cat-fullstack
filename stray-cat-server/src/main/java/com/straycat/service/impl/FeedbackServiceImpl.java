package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.Feedback;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.FeedbackMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.FeedbackService;
import com.straycat.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final int CONTENT_MAX = 500;

    private static final int MAX_IMAGES = 3;

    /** 24 小时内最多提交条数，个人开发者没有人工客服，先靠限频挡刷 */
    private static final int DAILY_LIMIT = 3;

    private static final int MAX_PAGE_SIZE = 50;

    private static final int NOTIFY_SUMMARY = 30;

    private final FeedbackMapper feedbackMapper;

    private final SysUserMapper sysUserMapper;

    private final ContentSecurityService contentSecurityService;

    private final NotifyService notifyService;

    @Override
    public Feedback create(Long userId, Integer type, String content, String images) {
        String text = normalize(content);
        int feedbackType = normalizeType(type);
        String imagePaths = normalizeImages(images);

        contentSecurityService.check(userId, ContentSecurityService.SCENE_COMMENT, text);

        Long recent = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .gt(Feedback::getCreatedAt, LocalDateTime.now().minusHours(24)));
        if (recent != null && recent >= DAILY_LIMIT) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "24 小时内最多提交 " + DAILY_LIMIT + " 条反馈，请明天再来");
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(feedbackType);
        feedback.setContent(text);
        feedback.setImages(imagePaths);
        feedback.setStatus(Feedback.STATUS_PENDING);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);

        // 提醒全部管理员来处理；没配订阅模板就只落消息中心，业务不受影响
        String summary = text.length() > NOTIFY_SUMMARY ? text.substring(0, NOTIFY_SUMMARY) + "…" : text;
        notifyService.sendToAdmins(MessageType.FEEDBACK_NEW, feedback.getId(),
                notifyService.displayName(userId) + " 提交了反馈：" + summary, java.util.Map.of());
        return feedback;
    }

    @Override
    public Page<Feedback> listMine(Long userId, Integer pageNum, Integer pageSize) {
        return feedbackMapper.selectPage(new Page<>(pageNo(pageNum), size(pageSize)),
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreatedAt)
                        .orderByDesc(Feedback::getId));
    }

    @Override
    public Page<Feedback> page(Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .orderByAsc(Feedback::getStatus)
                .orderByDesc(Feedback::getCreatedAt);
        if (status != null) {
            wrapper.eq(Feedback::getStatus, status);
        }
        Page<Feedback> page = feedbackMapper.selectPage(new Page<>(pageNo(pageNum), size(pageSize)), wrapper);
        fillNicknames(page.getRecords());
        return page;
    }

    /**
     * 后台列表要看到提交人是谁，一次把这页的昵称全捞出来，不做 N+1
     */
    private void fillNicknames(List<Feedback> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream().map(Feedback::getUserId).distinct().toList();
        Map<Long, String> nicknames = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        user -> user.getNickname() != null ? user.getNickname() : user.getUsername()));
        records.forEach(feedback -> feedback.setNickname(nicknames.get(feedback.getUserId())));
    }

    @Override
    public void handle(Long id, Integer status, String reply) {
        if (id == null || status == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "反馈不存在");
        }
        if (status != Feedback.STATUS_REPLIED && status != Feedback.STATUS_IGNORED) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的处理状态");
        }
        if (status == Feedback.STATUS_REPLIED) {
            String text = reply == null ? "" : reply.trim();
            if (text.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "回复内容不能为空");
            }
            if (text.length() > CONTENT_MAX) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "回复最多 " + CONTENT_MAX + " 字");
            }
            feedback.setReply(text);
            // 已回复过的再补一条回复，只更新内容不重复打扰
            boolean firstReply = feedback.getStatus() != Feedback.STATUS_REPLIED;
            feedback.setStatus(status);
            feedback.setRepliedAt(LocalDateTime.now());
            feedbackMapper.updateById(feedback);

            if (firstReply) {
                String summary = text.length() > NOTIFY_SUMMARY ? text.substring(0, NOTIFY_SUMMARY) + "…" : text;
                notifyService.send(MessageType.FEEDBACK_REPLIED, feedback.getUserId(), feedback.getId(),
                        "你的反馈「" + summary + "」已回复，请在反馈页查看");
            }
            return;
        }
        feedback.setStatus(Feedback.STATUS_IGNORED);
        feedback.setRepliedAt(LocalDateTime.now());
        feedbackMapper.updateById(feedback);
    }

    private int normalizeType(Integer type) {
        if (type == null) {
            return Feedback.TYPE_SUGGESTION;
        }
        if (type != Feedback.TYPE_SUGGESTION && type != Feedback.TYPE_BUG && type != Feedback.TYPE_OTHER) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "反馈类型不合法");
        }
        return type;
    }

    private String normalize(String raw) {
        String content = raw == null ? "" : raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (content.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "反馈内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "反馈内容最多 " + CONTENT_MAX + " 字");
        }
        return content;
    }

    /**
     * 前端传逗号分隔的 /uploads/xxx 相对路径；只保留前 3 张，超出直接截断不报错
     */
    private String normalizeImages(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> paths = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(path -> path.startsWith("/uploads/") && path.length() < 200)
                .limit(MAX_IMAGES)
                .toList();
        return paths.isEmpty() ? null : String.join(",", paths);
    }

    private long pageNo(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private long size(Integer pageSize) {
        int value = pageSize == null || pageSize < 1 ? 20 : pageSize;
        return Math.min(value, MAX_PAGE_SIZE);
    }
}
