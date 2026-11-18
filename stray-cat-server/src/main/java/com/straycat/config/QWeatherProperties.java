package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 和风天气（QWeather）开发配置。
 *
 * 2026 最新版：公共 API 地址已停用，必须使用开发者独立 API Host（形如 xxxx.qweatherapi.com），
 * 鉴权用 API Key 请求头 X-QW-Api-Key（或 JWT Bearer，本项目用 API Key，量小且 Key 只在服务端）。
 *
 * host / key 只留在服务端，由环境变量注入，不写进仓库、不下发小程序包。
 */
@Data
@Component
@ConfigurationProperties(prefix = "qweather")
public class QWeatherProperties {

    /** 开发者独立 API Host，不含协议头，如 h2a9cf3mhs.xy.qweatherapi.com */
    private String host = "";

    /** API Key 凭据 */
    private String key = "";

    /** 连接超时（毫秒）：容器内首次连接和风偶发较慢，放宽到 6s */
    private int connectTimeoutMs = 6000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 10000;

    public boolean ready() {
        return host != null && !host.isBlank() && key != null && !key.isBlank();
    }

    /** 归一化为可用 base url，去掉用户可能误带的协议头/尾斜杠 */
    public String baseUrl() {
        String h = host.trim();
        h = h.replaceFirst("^https?://", "");
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        return "https://" + h;
    }
}
