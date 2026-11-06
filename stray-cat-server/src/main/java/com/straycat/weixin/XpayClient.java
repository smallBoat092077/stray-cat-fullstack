package com.straycat.weixin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.config.XpayProperties;
import com.straycat.util.XpaySigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * xpay 服务端接口。请求体先序列化成字符串，签名的就是这一个字符串，
 * 发出的也必须是同一个字符串——两次序列化等于签名必错。
 */
@Slf4j
@Component
public class XpayClient {

    private static final String QUERY_ORDER_URI = "/xpay/query_order";

    @Resource
    private XpayProperties xpayProperties;

    @Resource
    private WxAccessTokenService accessTokenService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询订单。返回微信原始 JSON，其中 order.status：
     * 1 创建成功 2 已支付待发货 3 发货中 4 已发货 5 退款(商户扣款完成) 6 已关闭 8 退款(用户收款完成)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryOrder(String openid, String outTradeNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", openid);
        body.put("env", xpayProperties.getEnv());
        body.put("order_id", outTradeNo);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("订单查询参数序列化失败", e);
        }

        String paySig = XpaySigner.paySig(xpayProperties.getAppKey(), QUERY_ORDER_URI, payload);
        String url = "https://api.weixin.qq.com/xpay/query_order?access_token={token}&pay_sig={sig}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class,
                    Map.of("token", accessTokenService.get(), "sig", paySig));
            log.debug("query_order 请求 {} 响应 {}", payload, response.getBody());
            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            log.warn("query_order 调用失败 outTradeNo={}: {}", outTradeNo, e.getMessage());
            return null;
        }
    }
}
