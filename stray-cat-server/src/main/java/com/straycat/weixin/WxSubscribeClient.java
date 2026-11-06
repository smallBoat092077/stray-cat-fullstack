package com.straycat.weixin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.config.WxSubscribeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订阅消息发送。一次订阅只能发一条（微信一次性订阅的硬限制），
 * 所以调用方要珍惜每次触发点，发送失败不重试、只记日志——站内消息才是可靠的底座。
 */
@Slf4j
@Component
public class WxSubscribeClient {

    private static final String API_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token={token}";
    /** 用户没有可用的订阅额度，属正常业务情形，不打错误日志 */
    private static final int ERRCODE_NO_QUOTA = 43101;
    private static final int ERRCODE_INVALID_TOKEN = 40001;
    private static final int ERRCODE_TOKEN_EXPIRED = 42001;

    @Resource
    private WxAccessTokenService accessTokenService;

    @Resource
    private WxSubscribeProperties properties;

    private final RestTemplate restTemplate = buildRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean send(String templateId, String openid, String page, Map<String, String> data) {
        if (templateId == null || openid == null || openid.isBlank()) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("page", page);
        body.put("miniprogram_state", properties.getState());
        body.put("lang", "zh_CN");
        body.put("data", toWxData(data));

        for (int attempt = 1; attempt <= 2; attempt++) {
            String token = accessTokenService.get();
            if (token.isEmpty()) {
                log.warn("订阅消息未发送：access_token 获取失败, template={}", templateId);
                return false;
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<String> response = restTemplate.postForEntity(API_URL,
                        new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                        String.class, Map.of("token", token));
                Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
                int errcode = parsed.get("errcode") instanceof Number n ? n.intValue() : -1;
                if (errcode == 0) {
                    return true;
                }
                if (errcode == ERRCODE_NO_QUOTA) {
                    log.info("订阅消息跳过：用户无可用订阅额度 openid={}", openid);
                    return false;
                }
                if ((errcode == ERRCODE_INVALID_TOKEN || errcode == ERRCODE_TOKEN_EXPIRED) && attempt == 1) {
                    accessTokenService.invalidate();
                    continue;
                }
                log.warn("订阅消息发送失败: errcode={}, errmsg={}, template={}",
                        errcode, parsed.get("errmsg"), templateId);
                return false;
            } catch (Exception e) {
                log.warn("订阅消息发送异常: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 微信要求的字段结构是 {"thing1":{"value":"..."}}，调用方只关心值
     */
    private Map<String, Object> toWxData(Map<String, String> data) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((key, value) -> wrapped.put(key, Map.of("value", value == null ? "" : value)));
        }
        return wrapped;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
