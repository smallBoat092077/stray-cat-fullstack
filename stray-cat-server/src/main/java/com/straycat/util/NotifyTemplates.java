package com.straycat.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订阅消息的字段装配。小程序后台申请的模板字段名必须与这里的 key 对齐
 * （thing=文本、time=时间），换模板时改这个类，不要散落到各个 Service。
 */
public final class NotifyTemplates {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NotifyTemplates() {
    }

    /**
     * 投喂相关：猫咪 / 相关人 / 时间
     */
    public static Map<String, String> feeding(String catName, String actor, LocalDateTime time) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("thing1", blankTo(catName, "猫咪"));
        data.put("thing2", blankTo(actor, "志愿者"));
        data.put("time3", format(time));
        return data;
    }

    /**
     * 领养相关：猫咪 / 申请人 / 审核意见
     */
    public static Map<String, String> adoption(String catName, String applicant, String comment) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("thing1", blankTo(catName, "猫咪"));
        data.put("thing2", blankTo(applicant, "申请人"));
        data.put("thing3", blankTo(comment, "已处理"));
        return data;
    }

    /**
     * 回访提醒：猫咪 / 领养通过时间 / 距今天数
     */
    public static Map<String, String> followUp(String catName, LocalDateTime adoptedAt, String stage) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("thing1", blankTo(catName, "猫咪"));
        data.put("time2", format(adoptedAt));
        data.put("thing3", blankTo(stage, "该提交回访了"));
        return data;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : TIMESTAMP.format(time);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
