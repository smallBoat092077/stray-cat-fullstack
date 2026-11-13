package com.straycat.config;

import com.straycat.util.NotifyTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 订阅消息「槽位 → 模板字段名」的回归测试。
 *
 * 背景：公共模板库里形状相同的模板，字段序号并不固定（有 thing1/thing2/thing3，
 * 也有 thing1/thing5/thing6、thing3/thing4/phrase5）。字段名与模板 content 不是逐字相等，
 * 微信就回 47003，而且只有在用户真有订阅额度时才会暴露，很容易配错上线后才发现。
 * 这里把线上实际模板的字段顺序锁住，改模板时先改 application.yml 再改这里。
 */
class WxSubscribeFieldsTest {

    /** 从 classpath 的 application.yml 绑定，走 Binder.get(env) 以便解析 ${ENV:默认值} */
    private WxSubscribeProperties load() throws IOException {
        ConfigurableEnvironment env = new StandardEnvironment();
        List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        sources.forEach(env.getPropertySources()::addLast);
        return Binder.get(env).bind("wechat.subscribe", WxSubscribeProperties.class)
                .orElseThrow(() -> new IllegalStateException("application.yml 缺少 wechat.subscribe"));
    }

    @Test
    @DisplayName("字段顺序与线上模板 content 逐字对齐")
    void fieldSpecMatchesRealTemplates() throws IOException {
        WxSubscribeProperties props = load();
        // 接单提醒 thing13/thing10/time11
        assertEquals("thing13,thing10,time11", props.fieldSpec("taskAccepted"));
        // 打卡提醒 thing6/thing5/time1
        assertEquals("thing6,thing5,time1", props.fieldSpec("recordSubmitted"));
        // 打卡提醒（审核结果复用同模板）thing4/thing10/time9
        assertEquals("thing4,thing10,time9", props.fieldSpec("recordApproved"));
        assertEquals("thing4,thing10,time9", props.fieldSpec("recordRejected"));
        // 审核结果通知 thing20/thing11/phrase1
        assertEquals("thing20,thing11,phrase1", props.fieldSpec("adoptionApproved"));
        assertEquals("thing20,thing11,phrase1", props.fieldSpec("adoptionRejected"));
        // 访客提醒 thing20/time3/thing11
        assertEquals("thing20,time3,thing11", props.fieldSpec("followUpDue"));
        // 记账邀请通知 thing1/thing2/thing3/time4（4 个字段，第 4 槽位是时间）
        assertEquals("thing1,thing2,thing3,time4", props.fieldSpec("coAdoptionInvite"));
        // 审核结果通知 thing20/thing11/phrase1（共同领养回应复用领养审核同款模板）
        assertEquals("thing20,thing11,phrase1", props.fieldSpec("coAdoptionResult"));
    }

    @Test
    @DisplayName("模板 ID 与字段配置都能按驼峰/短横线两种写法命中")
    void keyMatchingIsRelaxed() throws IOException {
        WxSubscribeProperties props = load();
        assertEquals(props.fieldSpec("taskAccepted"), props.fieldSpec("task-accepted"));
        assertEquals(props.fieldSpec("coAdoptionResult"), props.fieldSpec("co-adoption-result"));
        assertNull(props.templateId("not-exists"));
    }

    @Test
    @DisplayName("领养槽位重排到 thing3/thing4/phrase5")
    void adoptionSlotsRemapped() {
        Map<String, String> slots = NotifyTemplates.adoption("橘猫", "小李", "已同意");
        Map<String, String> data = NotifyTemplates.remap(slots, "thing3,thing4,phrase5");
        assertEquals(3, data.size());
        assertEquals("橘猫", data.get("thing3"));
        assertEquals("小李", data.get("thing4"));
        assertEquals("已同意", data.get("phrase5"));
    }

    @Test
    @DisplayName("共同领养邀请槽位重排到 thing1/thing2/thing3/time4")
    void coAdoptInviteSlotsRemapped() {
        Map<String, String> slots = NotifyTemplates.coAdoptInvite("张三", "小李", "邀请你共同领养「橘猫」",
                LocalDateTime.of(2026, 11, 13, 2, 10, 0));
        Map<String, String> data = NotifyTemplates.remap(slots, "thing1,thing2,thing3,time4");
        assertEquals(4, data.size());
        assertEquals("张三", data.get("thing1"));
        assertEquals("小李", data.get("thing2"));
        assertEquals("邀请你共同领养「橘猫」", data.get("thing3"));
        assertEquals("2026-11-13 02:10:00", data.get("time4"));
    }

    @Test
    @DisplayName("投喂槽位的时间值落在 time 字段且格式为 yyyy-MM-dd HH:mm:ss")
    void feedingTimeRemapped() {
        Map<String, String> slots = NotifyTemplates.feeding("橘猫", "张三",
                LocalDateTime.of(2026, 11, 9, 23, 45, 28));
        Map<String, String> data = NotifyTemplates.remap(slots, "thing6,thing5,time1");
        assertEquals("橘猫", data.get("thing6"));
        assertEquals("张三", data.get("thing5"));
        assertEquals("2026-11-09 23:45:28", data.get("time1"));
    }

    @Test
    @DisplayName("空值走兜底文案，不会把 null 发给微信")
    void blankValuesFallback() {
        Map<String, String> slots = NotifyTemplates.adoption(null, "  ", "");
        Map<String, String> data = NotifyTemplates.remap(slots, "thing1,thing2,thing3");
        assertEquals("猫咪", data.get("thing1"));
        assertEquals("申请人", data.get("thing2"));
        assertEquals("已处理", data.get("thing3"));
    }

    @Test
    @DisplayName("字段配置为空时原样返回，不至于发送空 data")
    void emptySpecKeepsSlots() {
        Map<String, String> slots = NotifyTemplates.followUp("橘猫", LocalDateTime.now(), "领养满 30 天");
        assertEquals(slots, NotifyTemplates.remap(slots, " "));
    }
}
