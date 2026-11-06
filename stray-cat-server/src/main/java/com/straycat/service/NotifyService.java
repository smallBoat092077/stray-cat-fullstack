package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.MessageType;
import com.straycat.entity.UserMessage;

import java.util.Map;

public interface NotifyService {

    /**
     * 写站内消息，并在配置了模板 ID 时尽力补发一条微信订阅消息。
     * 任何失败都只记日志，不能把审核、接单这些业务动作带崩。
     */
    void send(MessageType type, Long userId, Long bizId, String content, Map<String, String> templateData);

    default void send(MessageType type, Long userId, Long bizId, String content) {
        send(type, userId, bizId, content, Map.of());
    }

    /**
     * 待办下发给全部管理员（角色 3）。管理员是个位数，不做分组和去重。
     */
    void sendToAdmins(MessageType type, Long bizId, String content, Map<String, String> templateData);

    /**
     * 通知文案里的用户名：昵称优先，没有昵称退回账号名，都查不到给「志愿者」
     */
    String displayName(Long userId);

    Page<UserMessage> listMine(Long userId, Integer pageNum, Integer pageSize);

    long unreadCount(Long userId);

    int markAllRead(Long userId);
}
