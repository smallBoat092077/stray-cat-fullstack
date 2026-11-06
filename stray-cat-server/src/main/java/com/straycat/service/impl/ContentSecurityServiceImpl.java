package com.straycat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.common.ResultCode;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.ContentSecurityService;
import com.straycat.weixin.WxAccessTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信内容安全 msg_sec_check（version=2）。
 *
 * 默认策略：risky 直接拒绝，review 与「接口不可用」只记日志放行。
 * 提审前可把 strict 打开，让 review 和接口异常也拦住。
 */
@Slf4j
@Service
public class ContentSecurityServiceImpl implements ContentSecurityService {

    private static final int MAX_CONTENT_LENGTH = 2500;
    private static final String API_URL =
            "https://api.weixin.qq.com/wxa/msg_sec_check?access_token={token}";

    @Value("${wechat.msg-sec-check.enabled:true}")
    private boolean enabled;

    @Value("${wechat.msg-sec-check.strict:false}")
    private boolean strict;

    @Resource
    private WxAccessTokenService accessTokenService;

    @Resource
    private SysUserMapper sysUserMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 打赏、上报这些调用点都处在一个事务里，超时必须收紧，不能让微信拖住数据库连接。
     */
    private final RestTemplate restTemplate = buildRestTemplate();

    @Override
    public void check(Long userId, int scene, String... parts) {
        if (!enabled) {
            return;
        }
        String content = join(parts);
        if (content.isEmpty()) {
            return;
        }

        SysUser user = sysUserMapper.selectById(userId);
        String openid = user == null ? null : user.getOpenid();
        if (openid == null || openid.isBlank()) {
            // 只有微信登录的用户才有 openid，账号密码注册的用户走不到这个接口
            log.warn("内容安全检测跳过：user={} 无 openid", userId);
            return;
        }

        String accessToken = accessTokenService.get();
        if (accessToken.isEmpty()) {
            fail("access_token 获取失败", userId, null);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("version", 2);
        body.put("scene", scene);
        body.put("openid", openid);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            fail("检测参数序列化失败", userId, e);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String responseBody;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    API_URL, new HttpEntity<>(payload, headers), String.class,
                    Map.of("token", accessToken));
            responseBody = response.getBody();
        } catch (Exception e) {
            fail("msg_sec_check 网络异常", userId, e);
            return;
        }

        judge(user, responseBody);
    }

    @SuppressWarnings("unchecked")
    private void judge(SysUser user, String responseBody) {
        Long userId = user.getId();
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(responseBody, Map.class);
        } catch (Exception e) {
            fail("msg_sec_check 响应无法解析: " + responseBody, userId, e);
            return;
        }

        int errcode = parsed.get("errcode") instanceof Number n ? n.intValue() : 0;
        if (errcode != 0) {
            if (errcode == 40001 || errcode == 42001) {
                accessTokenService.invalidate();
            }
            fail("msg_sec_check errcode=" + errcode + " errmsg=" + parsed.get("errmsg"), userId, null);
            return;
        }

        Object resultNode = parsed.get("result");
        Map<String, Object> result = resultNode instanceof Map ? (Map<String, Object>) resultNode : parsed;
        String suggest = result.get("suggest") == null ? "" : result.get("suggest").toString();
        Object label = result.get("label");

        switch (suggest) {
            case "risky" -> {
                log.warn("内容安全拦截：user={} openid={} label={} detail={}",
                        userId, user.getOpenid(), label, truncate(parsed.get("detail")));
                throw new BusinessException(ResultCode.CONTENT_RISKY);
            }
            case "review" -> {
                log.warn("内容安全待复审：user={} label={} detail={}，strict={} 决定{}",
                        userId, label, truncate(parsed.get("detail")), strict, strict ? "拦截" : "放行");
                if (strict) {
                    throw new BusinessException(ResultCode.CONTENT_RISKY);
                }
            }
            default -> log.debug("内容安全通过：user={} suggest={}", userId, suggest);
        }
    }

    private void fail(String reason, Long userId, Exception e) {
        log.warn("内容安全检测不可用（user={}）：{}", userId, reason, e);
        if (strict) {
            throw new BusinessException(ResultCode.CONTENT_CHECK_UNAVAILABLE);
        }
    }

    private String join(String... parts) {
        if (parts == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(part.trim());
        }
        return sb.length() > MAX_CONTENT_LENGTH
                ? sb.substring(0, MAX_CONTENT_LENGTH) : sb.toString();
    }

    private String truncate(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replaceAll("\\s+", " ");
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
