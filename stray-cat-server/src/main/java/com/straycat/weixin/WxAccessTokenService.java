package com.straycat.weixin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全局 access_token。同一个 appid 的 token 在微信侧是单例，多个调用方（虚拟支付查单、
 * 内容安全检测）必须共用一份缓存，否则互相把对方的 token 刷掉。
 */
@Slf4j
@Service
public class WxAccessTokenService {

    private static final String CACHE_KEY = "wx:access_token";

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return access_token，取不到时返回空串（调用方按「接口不可用」处理）
     */
    @SuppressWarnings("unchecked")
    public String get() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached.toString();
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential"
                + "&appid=" + appid + "&secret=" + secret;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
            Object token = parsed.get("access_token");
            if (token == null) {
                log.error("获取 access_token 失败: {}", response.getBody());
                return "";
            }
            long expiresIn = parsed.get("expires_in") instanceof Number n
                    ? n.longValue() : 7200L;
            redisTemplate.opsForValue().set(CACHE_KEY, token.toString(),
                    Math.max(60, expiresIn - 300), TimeUnit.SECONDS);
            return token.toString();
        } catch (Exception e) {
            log.error("获取 access_token 异常", e);
            return "";
        }
    }

    public void invalidate() {
        redisTemplate.delete(CACHE_KEY);
    }
}
