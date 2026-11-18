package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 花纹识别服务（stray-cat-recognizer）配置。阈值以本服务 app.env 为唯一判定口径：
 * 二期已删除 Python 侧 MATCH_THRESHOLD 硬编码，Python 只返回按分数降序的候选，
 * 高/中/低置信与 margin 判定全部由 Java 侧完成。
 *
 * <p>风格与 TencentMapProperties / XpayProperties 一致：@Component + @ConfigurationProperties，
 * 由组件扫描发现并绑定 application.yml 的 recognizer.* 段。
 */
@Data
@Component
@ConfigurationProperties(prefix = "recognizer")
public class RecognizerProperties {

    /** 内网识别服务根地址 */
    private String baseUrl = "http://recognizer:8000";

    /** 灰度开关：false 时所有转发直接短路降级，不计失败 */
    private boolean enabled = false;

    /** HttpClient 连接超时（毫秒） */
    private int connectTimeoutMs = 2000;

    /**
     * 单次请求整体超时（毫秒，用 HttpRequest.timeout），用于 /stats。
     * 二期 UAT 实测：识别服务刚重启后首次 /stats 要同时建立 MySQL 连接，5s 会偶发
     * request timed out 被误判为降级（2026-11-15 10:24 复现），放宽到 8000ms。
     */
    private int readTimeoutMs = 8000;

    /**
     * /match 专用整体超时（毫秒）。
     * UAT 实测：Python /match 冷启动首次 4.8s、稳态 0.33s；一期的 2500ms 会让冷启动后的
     * 第一个用户请求必然被判超时降级（2026-11-15 真机实测复现）。二期放宽到 8000ms，
     * 并配合 RecognizerWarmupRunner 的失败退避重试根治冷启动。
     */
    private int matchTimeoutMs = 8000;

    /**
     * 登记基准图（/register）独立读超时（毫秒）。
     * UAT 实测经 Java 转发首次约 9.6s（含特征提取、FAISS 索引更新与 MySQL 写入），
     * 写操作必须给足超时，不能靠重试补偿，否则会出现「前端报失败、库里已登记」。
     */
    private int registerTimeoutMs = 15000;

    /** 高置信阈值 best >= M */
    private BigDecimal matchThreshold = new BigDecimal("0.85");

    /** 人工确认下限 best >= L */
    private BigDecimal manualThreshold = new BigDecimal("0.70");

    /**
     * 二期：判 high 还要求 top1 - top2 >= margin，否则降为 medium。
     * 避免多个候选分数挤在一起时 indiscriminately 判高置信（一期 0.99 假阳性的放大环节）。
     */
    private BigDecimal marginThreshold = new BigDecimal("0.05");

    /** /match 默认返回候选数 */
    private int topK = 3;

    /** 连续失败熔断后的冷却时长（秒） */
    private int failureCooldownSeconds = 30;
}
