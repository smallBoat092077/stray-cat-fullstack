package com.straycat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.config.XpayProperties;
import com.straycat.service.RechargeService;
import com.straycat.util.XpaySigner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信虚拟支付的消息推送回调。发货通知是主要的到账来源，签名校验不过一律拒绝，
 * 否则这个公开 POST 就成了给别人加猫粮的口子。
 */
@Slf4j
@Tag(name = "虚拟支付回调")
@RestController
@RequestMapping("/xpay")
@RequiredArgsConstructor
public class XpayNotifyController {

    private static final String EVENT_DELIVER = "xpay_goods_deliver_notify";
    private static final String EVENT_REFUND = "xpay_refund_notify";
    private static final String REPLY_SUCCESS =
            "<xml><ErrCode>0</ErrCode><ErrMsg><![CDATA[success]]></ErrMsg></xml>";

    private final RechargeService rechargeService;
    private final XpayProperties xpayProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "消息推送地址校验")
    @GetMapping("/notify")
    public String verify(@RequestParam(name = "signature", required = false) String signature,
                         @RequestParam(name = "timestamp", required = false) String timestamp,
                         @RequestParam(name = "nonce", required = false) String nonce,
                         @RequestParam(name = "echostr", required = false) String echostr) {
        if (verified(signature, timestamp, nonce)) {
            return echostr == null ? "" : echostr;
        }
        log.warn("消息推送地址校验失败，请检查 xpay.push-token 与后台配置是否一致");
        return "invalid signature";
    }

    @Operation(summary = "发货/退款通知")
    @PostMapping("/notify")
    public String notify(@RequestParam(name = "signature", required = false) String signature,
                         @RequestParam(name = "timestamp", required = false) String timestamp,
                         @RequestParam(name = "nonce", required = false) String nonce,
                         @RequestBody String rawBody) {
        if (!verified(signature, timestamp, nonce)) {
            log.warn("虚拟支付回调签名校验不通过，已忽略: {}", abbreviate(rawBody));
            return reject("signature check failed");
        }

        Map<String, String> message;
        try {
            message = rawBody.trim().startsWith("{") ? readJson(rawBody) : readXml(rawBody);
        } catch (Exception e) {
            log.warn("虚拟支付回调报文解析失败: {}", e.getMessage());
            return reject("bad payload");
        }

        String event = message.get("Event");
        String outTradeNo = message.get("OutTradeNo");
        String wxOrderNo = message.get("WeChatPayInfo.MchOrderNo");
        if (wxOrderNo == null) {
            wxOrderNo = message.get("WxorderNo");
        }

        if (EVENT_DELIVER.equals(event)) {
            boolean handled = rechargeService.deliverByOutTradeNo(outTradeNo, wxOrderNo);
            log.info("发货通知 outTradeNo={} productId={} 处理结果={}",
                    outTradeNo, message.get("GoodsInfo.ProductId"), handled);
            return REPLY_SUCCESS;
        }
        if (EVENT_REFUND.equals(event)) {
            log.info("退款通知 outTradeNo={} 处理结果={}",
                    outTradeNo, rechargeService.refundByOutTradeNo(outTradeNo, wxOrderNo));
            return REPLY_SUCCESS;
        }
        log.debug("忽略无关事件 {} payload={}", event, abbreviate(rawBody));
        return REPLY_SUCCESS;
    }

    private boolean verified(String signature, String timestamp, String nonce) {
        String token = xpayProperties.getPushToken();
        if (token == null || token.isBlank()) {
            log.debug("未配置 xpay.push-token，拒绝回调");
            return false;
        }
        if (signature == null || timestamp == null || nonce == null) {
            return false;
        }
        return signature.equalsIgnoreCase(XpaySigner.pushSignature(token, timestamp, nonce));
    }

    private String reject(String reason) {
        return "<xml><ErrCode>1</ErrCode><ErrMsg><![CDATA[" + reason + "]]></ErrMsg></xml>";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readJson(String rawBody) throws Exception {
        Map<String, Object> root = objectMapper.readValue(rawBody, Map.class);
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", root, flat);
        return flat;
    }

    private Map<String, String> readXml(String rawBody) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(rawBody)));
        Map<String, String> flat = new LinkedHashMap<>();
        walk(document.getDocumentElement(), "", flat);
        return flat;
    }

    private void walk(Element element, String prefix, Map<String, String> target) {
        NodeList children = element.getChildNodes();
        boolean hasElementChild = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChild = true;
                String key = prefix.isEmpty() ? node.getNodeName() : prefix + "." + node.getNodeName();
                walk((Element) node, key, target);
            }
        }
        if (!hasElementChild && !prefix.isEmpty()) {
            target.put(prefix, element.getTextContent());
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<String, Object>) value, target);
            } else if (value != null) {
                target.put(key, String.valueOf(value));
            }
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
