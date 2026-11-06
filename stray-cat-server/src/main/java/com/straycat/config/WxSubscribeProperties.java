package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信订阅消息模板 ID。key 用 MessageType 的 templateKey（驼峰， relaxed binding 会匹配 task-accepted 等写法）。
 * 后台「订阅消息」里申请到模板后才填，未填的消息类型只落站内消息。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.subscribe")
public class WxSubscribeProperties {

    private boolean enabled = true;

    /** 跳转小程序版本：formal 正式版 / trial 体验版 / developer 开发版 */
    private String state = "formal";

    private Map<String, String> templates = new HashMap<>();

    /**
     * yml 里可以写 task-accepted 也可以写 taskAccepted，Spring 对 Map 键的保留规则不完全可靠，
     * 所以这里按「去掉大小写和分隔符」归一化匹配。
     */
    public String templateId(String key) {
        if (key == null || templates.isEmpty()) {
            return null;
        }
        String target = normalize(key);
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            if (normalize(entry.getKey()).equals(target)) {
                String value = entry.getValue();
                return value == null || value.isBlank() ? null : value.trim();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public boolean ready() {
        return enabled;
    }
}
