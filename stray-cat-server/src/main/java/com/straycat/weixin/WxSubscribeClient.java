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

    /** 推送结果码，与 user_message.push_status 一一对应 */
    public static final int PUSH_ONLY_IN_APP = 0;
    public static final int PUSH_OK = 1;
    public static final int PUSH_NO_QUOTA = 2;
    public static final int PUSH_FAILED = 3;
    public static final int PUSH_NO_OPENID = 4;

    /**
     * @param status  结果码，直接落 user_message.push_status
     * @param message 失败原因摘要，落 user_message.push_error（仅排查用）
     */
    public record SendResult(int status, String message) {

        static SendResult ok() {
            return new SendResult(PUSH_OK, null);
        }

        static SendResult of(int status, String message) {
            return new SendResult(status, message);
        }
    }

    @Resource
    private WxAccessTokenService accessTokenService;

    @Resource
    private WxSubscribeProperties properties;

    private final RestTemplate restTemplate = buildRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SendResult send(String templateId, String openid, String page, Map<String, String> data) {
        if (templateId == null || templateId.isBlank()) {
            return SendResult.of(PUSH_ONLY_IN_APP, null);
        }
        if (openid == null || openid.isBlank()) {
            // 账号密码注册的用户没有 openid，只能看站内消息
            return SendResult.of(PUSH_NO_OPENID, "未绑定微信");
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
                return SendResult.of(PUSH_FAILED, "access_token 获取失败");
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
                    return SendResult.ok();
                }
                if (errcode == ERRCODE_NO_QUOTA) {
                    log.info("订阅消息跳过：用户无可用订阅额度 openid={}", openid);
                    return SendResult.of(PUSH_NO_QUOTA, "用户未授权或额度已用完");
                }
                if ((errcode == ERRCODE_INVALID_TOKEN || errcode == ERRCODE_TOKEN_EXPIRED) && attempt == 1) {
                    accessTokenService.invalidate();
                    continue;
                }
                log.warn("订阅消息发送失败: errcode={}, errmsg={}, template={}",
                        errcode, parsed.get("errmsg"), templateId);
                return SendResult.of(PUSH_FAILED, "errcode=" + errcode + " " + parsed.get("errmsg"));
            } catch (Exception e) {
                log.warn("订阅消息发送异常: {}", e.getMessage());
                return SendResult.of(PUSH_FAILED, e.getMessage());
            }
        }
        return SendResult.of(PUSH_FAILED, "重试后仍失败");
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
