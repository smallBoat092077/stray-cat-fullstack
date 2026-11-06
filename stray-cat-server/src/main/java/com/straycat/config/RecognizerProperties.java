package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 花纹识别服务配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "recognizer")
public class RecognizerProperties {

    /**
     * 识别服务基础 URL
     * 例如：http://recognizer:8000 或 http://47.95.149.170:8001
     */
    private String baseUrl = "http://recognizer:8000";

    /**
     * 匹配阈值（0-1）
     * 相似度 >= 此值认为匹配成功
     */
    private double matchThreshold = 0.85;

    /**
     * 人工确认阈值下限
     * 相似度在此值和 matchThreshold 之间需要人工确认
     */
    private double manualThreshold = 0.70;
}
