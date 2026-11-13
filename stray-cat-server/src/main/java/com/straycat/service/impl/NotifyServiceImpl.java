package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.config.WxSubscribeProperties;
import com.straycat.entity.SysUser;
import com.straycat.entity.UserMessage;
import com.straycat.mapper.SysUserMapper;
import com.straycat.mapper.UserMessageMapper;
import com.straycat.service.NotifyService;
import com.straycat.util.NotifyTemplates;
import com.straycat.weixin.WxSubscribeClient;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class NotifyServiceImpl implements NotifyService {

    private static final int TITLE_MAX = 60;
    private static final int CONTENT_MAX = 300;
    /** 订阅消息 thing 类字段上限 20 字，超了微信直接报错 */
    private static final int WX_THING_MAX = 20;
    /** 与 user_message.push_error 列宽一致 */
    private static final int PUSH_ERROR_MAX = 120;
    private static final int ROLE_ADMIN = 3;

    @Resource
    private UserMessageMapper userMessageMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private WxSubscribeProperties subscribeProperties;

    @Resource
    private WxSubscribeClient subscribeClient;

    /**
     * 发送动作挪到事务提交后的独立线程：一是没提交成功就不该通知用户，
     * 二是几秒级的微信 HTTP 调用不能挂在业务事务上占着数据库连接。
     */
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wx-subscribe-send");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void send(MessageType type, Long userId, Long bizId, String content, Map<String, String> templateData) {
        if (userId == null) {
            return;
        }
        String page = type.page(bizId);
        Long messageId = null;
        try {
            UserMessage message = new UserMessage();
            message.setUserId(userId);
            message.setType(type.name());
            message.setTitle(truncate(type.getTitle(), TITLE_MAX));
            message.setContent(truncate(content == null ? type.getTitle() : content, CONTENT_MAX));
            message.setPage(page);
            message.setBizId(bizId);
            message.setReadFlag(0);
            message.setPushStatus(WxSubscribeClient.PUSH_ONLY_IN_APP);
            message.setCreatedAt(LocalDateTime.now());
            userMessageMapper.insert(message);
            messageId = message.getId();
        } catch (Exception e) {
            log.warn("站内消息写入失败: type={}, user={}, {}", type, userId, e.getMessage());
            return;
        }

        String templateId = subscribeProperties.templateId(type.getTemplateKey());
        if (templateId == null || !subscribeProperties.ready() || templateData == null || templateData.isEmpty()) {
            return;
        }
        // 槽位值 → 模板真实字段名（thing1/thing5/time2…），字段名由 wechat.subscribe.fields 决定
        Map<String, String> wxData = NotifyTemplates.remap(templateData, subscribeProperties.fieldSpec(type.getTemplateKey()));
        Long id = messageId;
        afterCommit(() -> pushSubscribe(id, templateId, userId, page, wxData));
    }

    @Override
    public void sendToAdmins(MessageType type, Long bizId, String content, Map<String, String> templateData) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getRole, ROLE_ADMIN).select(SysUser::getId);
        for (SysUser admin : sysUserMapper.selectList(wrapper)) {
            send(type, admin.getId(), bizId, content, templateData);
        }
    }

    @Override
    public String displayName(Long userId) {
        if (userId == null) {
            return "志愿者";
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return "志愿者";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername() == null || user.getUsername().isBlank() ? "志愿者" : user.getUsername();
    }

    @Override
    public Page<UserMessage> listMine(Long userId, Integer pageNum, Integer pageSize) {
        Page<UserMessage> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId).orderByDesc(UserMessage::getId);
        return userMessageMapper.selectPage(page, wrapper);
    }

    @Override
    public long unreadCount(Long userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId).eq(UserMessage::getReadFlag, 0);
        return userMessageMapper.selectCount(wrapper);
    }

    @Override
    public int markAllRead(Long userId) {
        return userMessageMapper.markAllRead(userId);
    }

    private void pushSubscribe(Long messageId, String templateId, Long userId, String page,
                               Map<String, String> templateData) {
        try {
            SysUser user = sysUserMapper.selectById(userId);
            String openid = user == null ? null : user.getOpenid();
            WxSubscribeClient.SendResult result;
            if (openid == null || openid.isBlank()) {
                result = new WxSubscribeClient.SendResult(WxSubscribeClient.PUSH_NO_OPENID, "未绑定微信");
            } else {
                result = subscribeClient.send(templateId, openid, page, clampData(templateData));
            }
            markPush(messageId, result);
        } catch (Exception e) {
            log.warn("订阅消息发送异常: user={}, {}", userId, e.getMessage());
            markPush(messageId, new WxSubscribeClient.SendResult(WxSubscribeClient.PUSH_FAILED, e.getMessage()));
        }
    }

    /**
     * 推送结果回写。发送在事务提交后的独立线程里跑，所以这里是一次独立更新；
     * 回写失败不影响业务，只丢排查线索。
     */
    private void markPush(Long messageId, WxSubscribeClient.SendResult result) {
        if (messageId == null || result == null) {
            return;
        }
        try {
            UserMessage update = new UserMessage();
            update.setId(messageId);
            update.setPushStatus(result.status());
            String error = result.message();
            update.setPushError(error == null ? null : truncate(error, PUSH_ERROR_MAX));
            userMessageMapper.updateById(update);
        } catch (Exception e) {
            log.warn("推送结果回写失败: message={}, {}", messageId, e.getMessage());
        }
    }

    /**
     * 只截 thing 类字段：微信对 thing 有 20 字上限，time（19 字）和 phrase（枚举）不能截，
     * 截了反而对不上模板。
     */
    private Map<String, String> clampData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, String> clamped = new java.util.LinkedHashMap<>();
        data.forEach((key, value) -> clamped.put(key,
                key != null && key.startsWith("thing") ? truncate(value, WX_THING_MAX) : value));
        return clamped;
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendExecutor.execute(task);
                }
            });
        } else {
            sendExecutor.execute(task);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        sendExecutor.shutdownNow();
    }
}
