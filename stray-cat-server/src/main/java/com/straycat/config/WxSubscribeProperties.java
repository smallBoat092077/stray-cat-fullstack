package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信订阅消息模板 ID 与字段名。key 用 MessageType 的 templateKey（驼峰，relaxed binding 会匹配 task-accepted 等写法）。
 * 后台「订阅消息」里申请到模板后才填，未填的消息类型只落站内消息。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.subscribe")
public class WxSubscribeProperties {

    private static final String DEFAULT_FIELD_SPEC = "thing1,thing2,thing3";

    private boolean enabled = true;

    /** 跳转小程序版本：formal 正式版 / trial 体验版 / developer 开发版 */
    private String state = "formal";

    private Map<String, String> templates = new HashMap<>();

    /**
     * 每个模板 content 里的字段名，逗号分隔，按位置对应 NotifyTemplates 的三个槽位。
     * 例：宠物养护记录提醒的 content 是 thing1/thing2/thing4，就要写 thing1,thing2,thing4。
     */
    private Map<String, String> fields = new HashMap<>();

    /**
     * yml 里可以写 task-accepted 也可以写 taskAccepted，Spring 对 Map 键的保留规则不完全可靠，
     * 所以这里按「去掉大小写和分隔符」归一化匹配。
     */
    public String templateId(String key) {
        String value = match(templates, key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 模板字段名顺序。未配置时按最常见的 thing1/thing2/thing3 兜底，
     * 配错会在发送时以 47003 落在 user_message.push_error 里。
     */
    public String fieldSpec(String key) {
        String spec = match(fields, key);
        return spec == null || spec.isBlank() ? DEFAULT_FIELD_SPEC : spec.trim();
    }

    private static String match(Map<String, String> map, String key) {
        if (key == null || map == null || map.isEmpty()) {
            return null;
        }
        String target = normalize(key);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (normalize(entry.getKey()).equals(target)) {
                return entry.getValue();
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
