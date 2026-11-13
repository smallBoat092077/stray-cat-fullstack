package com.straycat.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订阅消息的槽位装配。
 *
 * 这里只决定「第 1/2/3 个槽位放什么」；「槽位填到模板的哪个字段名」由
 * {@code wechat.subscribe.fields} 配置决定（见 WxSubscribeProperties#fieldSpec）。
 *
 * 为什么要拆开：公共模板库里形状相同的模板，字段序号并不固定——同样是三个 thing，
 * 可能是 thing1/thing2/thing3，也可能是 thing1/thing5/thing6。字段名与模板 content
 * 不是逐字相等，微信就回 47003。所以换模板只改配置，不要改这里。
 */
public final class NotifyTemplates {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NotifyTemplates() {
    }

    /**
     * 投喂相关：猫咪 / 相关人 / 时间
     */
    public static Map<String, String> feeding(String catName, String actor, LocalDateTime time) {
        return slots(blankTo(catName, "猫咪"), blankTo(actor, "志愿者"), format(time));
    }

    /**
     * 领养相关：猫咪 / 申请人 / 审核结果。
     * 注意第三个槽位对应模板的 phrase 字段（限 5 个汉字以内），只能传固定短语
     * （如「审核通过」「审核未通过」），不要传管理员自由评语，超长或含标点都会被微信 47003 拒收。
     */
    public static Map<String, String> adoption(String catName, String person, String comment) {
        return slots(blankTo(catName, "猫咪"), blankTo(person, "申请人"), blankTo(comment, "已处理"));
    }

    /**
     * 共同领养邀请：邀请人 / 被邀请人 / 备注 / 时间（4 个槽位，对应「记账邀请通知」）。
     * 模板字段少于 4 个时 remap 会自动丢弃多余的槽位，不会拼错。
     */
    public static Map<String, String> coAdoptInvite(String inviter, String invitee, String remark, LocalDateTime time) {
        Map<String, String> data = slots(blankTo(inviter, "志愿者"), blankTo(invitee, "用户"), blankTo(remark, "待确认"));
        data.put("slot4", format(time));
        return data;
    }

    /**
     * 回访提醒：猫咪 / 领养通过时间 / 阶段
     */
    public static Map<String, String> followUp(String catName, LocalDateTime adoptedAt, String stage) {
        return slots(blankTo(catName, "猫咪"), format(adoptedAt), blankTo(stage, "该提交回访了"));
    }

    /**
     * 按配置的字段名顺序重排槽位值。字段名必须与模板 content 逐字对齐，
     * 少了字段或拼错都会被微信判 47003，所以配置改动要和后台模板一起核对。
     */
    public static Map<String, String> remap(Map<String, String> slots, String fieldSpec) {
        if (slots == null || slots.isEmpty() || fieldSpec == null || fieldSpec.isBlank()) {
            return slots;
        }
        String[] fields = fieldSpec.split(",");
        Map<String, String> data = new LinkedHashMap<>();
        int index = 0;
        for (String value : slots.values()) {
            if (index >= fields.length) {
                break;
            }
            String field = fields[index].trim();
            if (!field.isEmpty()) {
                data.put(field, value);
            }
            index++;
        }
        return data;
    }

    private static Map<String, String> slots(String first, String second, String third) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("slot1", first);
        data.put("slot2", second);
        data.put("slot3", third);
        return data;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : TIMESTAMP.format(time);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
