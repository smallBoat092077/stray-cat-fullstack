package com.straycat.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 首页缓存读写（方案 2 / 7）。用 JSON 字符串存取，Redis 不可用时所有方法静默降级：
 * 读返回 null 让调用方直查数据库，写/失效直接放弃，绝不让首页接口 500。
 *
 * 复用 RedisConfig 里已定义的 RedisTemplate&lt;String,Object&gt;，不新增 Redis 依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeCache {

    public static final String BOOTSTRAP_PREFIX = "home:bootstrap:";

    public static final String BOARD_PREFIX = "home:board:";

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    public <T> T get(String key, Class<T> type) {
        String json = raw(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("首页缓存反序列化失败，降级直查: key={}", key);
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> type) {
        String json = raw(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("首页缓存反序列化失败，降级直查: key={}", key);
            return null;
        }
    }

    public void set(String key, Object value, long seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(seconds));
        } catch (Exception e) {
            log.warn("首页缓存写入失败，降级直查: key={}, err={}", key, e.getMessage());
        }
    }

    /** 后台内容发布/上下线时按前缀清除，如 home:bootstrap:* */
    public void evictByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("首页缓存失效失败（不影响业务）: prefix={}, err={}", prefix, e.getMessage());
        }
    }

    private String raw(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return cached == null ? null : cached.toString();
        } catch (Exception e) {
            log.warn("首页缓存读取失败，降级直查: key={}, err={}", key, e.getMessage());
            return null;
        }
    }
}
