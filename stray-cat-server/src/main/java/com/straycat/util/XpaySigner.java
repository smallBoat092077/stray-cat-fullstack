package com.straycat.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 微信虚拟支付签名工具。签名字符串必须与实际发出的报文逐字节一致，
 * 因此调用方应当先序列化出字符串，再把它原样用于签名和发送。
 */
public final class XpaySigner {

    private XpaySigner() {
    }

    /**
     * 支付级签名。前端调起时 uri 固定为 requestVirtualPayment，
     * 服务端调 xpay 接口时 uri 为不带 query 的接口路径，如 /xpay/query_order。
     */
    public static String paySig(String appKey, String uri, String payload) {
        return hmacSha256Hex(appKey, uri + "&" + payload);
    }

    /**
     * 用户态签名，密钥是该用户的 session_key
     */
    public static String userSig(String sessionKey, String payload) {
        return hmacSha256Hex(sessionKey, payload);
    }

    public static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * 消息推送服务器地址校验：sha1(token、timestamp、nonce 字典序拼接)
     */
    public static String pushSignature(String token, String timestamp, String nonce) {
        String[] parts = {token, timestamp, nonce};
        Arrays.sort(parts);
        String joined = parts[0] + parts[1] + parts[2];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return toHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 计算失败", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
